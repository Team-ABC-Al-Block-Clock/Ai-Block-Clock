"""브랜딩 payload 평가. 원본 응답 기준이며 Runtime 교정 후 성공률과 구분한다."""
from __future__ import annotations

import argparse
from collections import Counter
from copy import deepcopy
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import sys

from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parent
AI = ROOT.parent
RUNTIME = AI / "spike" / "linear-mvp"
NODES = {
    "01": ("01_market_research", "market_research"),
    "02": ("02_product_analysis", "product_analysis"),
    "03a": ("03a_strategy_differentiate", "strategy_draft"),
    "03b": ("03b_strategy_mass", "strategy_draft"),
    "04": ("04_brand_report", "brand_strategy"),
    "05": ("05_sns_campaign", "campaign_plan"),
    "06": ("06_ad_copy", "ad_copy"),
    "07": ("07_execution_plan", "execution_plan"),
}
FORBIDDEN = (
    "최고", "최상", "1위", "유일", "단 하나", "넘버원", "최초", "100%", "완벽",
    "절대", "보장", "반드시", "무조건", "영구", "타사 대비", "경쟁사보다",
    "업계 평균보다", "낫게 한다", "치료", "예방된다", "해결된다",
)
# 사전에 없는 채널과 인용하지 않은 새 카피는 사람이 확인해야 한다.
CHANNELS = ("인스타그램", "유튜브", "틱톡", "페이스북", "네이버 블로그", "카카오톡", "트위터", "링크드인")
MANUAL = {
    "01": ["경쟁사·시장 정보 사실성, comment 준수"],
    "02": ["약점 회피, 차별점 단순 복사, comment 준수, 경쟁사명"],
    "03a": ["포지션 충돌, 두 입력 근거 반영, 채널·일정 월권"],
    "03b": ["트렌드 반영, 필요성 전략, 없는 경쟁사, 채널·일정 월권"],
    "04": ["메시지 의미 중복, 축 의미 중복, 새 전략, 채널·일정 월권"],
    "05": ["채널 선정 근거, 세 메시지 반영, 게시물 의미 중복, 광고 문구 월권"],
    "06": ["헤드라인 의미 중복, 없는 기능·수치, 채널·일정 월권"],
    "07": ["사전 밖 채널, 인용하지 않은 새 카피, 게시물 일치, KPI 측정 반영"],
}


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def schema(kind):
    return read_json(AI / "schemas" / "catalog" / f"biz.{kind}@1.json")


def schema_errors(value, spec):
    return ["schema:" + (".".join(map(str, e.absolute_path)) or "$") + ":" + str(e.validator)
            for e in Draft202012Validator(spec).iter_errors(value)]


def validate_context(node, context):
    """입력 누락을 검사 성공으로 오인하지 않도록 API 호출 전에 검증한다."""
    parts = {
        "01": [(None, "brand_brief")], "02": [(None, "brand_brief")],
        "03a": [("market", "market_research"), ("product", "product_analysis")],
        "03b": [("market", "market_research"), ("product", "product_analysis")],
        "04": [("strategy_draft", "strategy_draft")],
        "05": [(None, "brand_strategy")], "06": [(None, "brand_strategy")],
        "07": [("campaign", "campaign_plan"), ("copy", "ad_copy")],
    }[node]
    if not isinstance(context, dict):
        raise ValueError("입력은 JSON 객체여야 합니다")
    for key, kind in parts:
        if schema_errors(context if key is None else context.get(key), schema(kind)):
            raise ValueError(f"입력 스키마 불일치: {key or kind}")
    if node == "04" and (not isinstance(context.get("brand_name"), str) or not context["brand_name"].strip()):
        raise ValueError("04 입력에는 brand_name과 strategy_draft가 필요합니다 (브랜드명 전달은 임시 가정)")
    if node in ("03a", "03b"):
        expected = "high" if node == "03a" else "low"
        if context["market"]["competition_level"] != expected:
            raise ValueError("선택한 노드와 입력 competition_level의 분기가 다릅니다")


def texts(value):
    if isinstance(value, str):
        return [value]
    if isinstance(value, dict):
        return [s for v in value.values() for s in texts(v)]
    if isinstance(value, list):
        return [s for v in value for s in texts(v)]
    return []


def check(node, output, context, expected_level=None):
    validate_context(node, context)
    spec = deepcopy(schema(NODES[node][1]))
    # 원본 LLM 출력 평가: Runtime 필드는 생략 또는 빈 문자열이어야 한다.
    if node == "04":
        spec["required"] = [k for k in spec["required"] if k not in ("issued_on", "author_org")]
    errors = schema_errors(output, spec)
    result = {"schema_pass": not errors, "issues": errors, "manual_review": list(MANUAL[node])}
    if errors:
        return result  # 잘못된 자료형을 내용 검사에 넘기지 않는다.
    issues = result["issues"]
    all_text = "\n".join(texts(output))
    if node == "01":
        reason = re.match(r"^전국 유통 브랜드 (\d+)개를 확인했다\.", output["level_reason"])
        if not reason:
            issues.append("market:reason_format")
        elif output["competition_level"] != ("high" if int(reason[1]) >= 5 else "low"):
            issues.append("market:reason_level_mismatch")
        if expected_level and output["competition_level"] != expected_level:
            issues.append("market:unexpected_level")
        if any(context["brand_name"] in c["name"] for c in output["competitors"]):
            issues.append("market:self_competitor")
        if re.search(r"\d[\d,.]*\s*(?:%|퍼센트|억|조|만원|원)", output["market_overview_md"]):
            issues.append("market:unsupported_statistic")
    if node in ("02", "03a", "04"):
        source = context["strategy_draft"] if node == "04" else context
        extra = set(re.findall(r"\d+(?:\.\d+)?", all_text)) - set(re.findall(r"\d+(?:\.\d+)?", "\n".join(texts(source))))
        if extra:
            result["manual_review"].append("숫자 근거 확인 필요 (입력에 없는 숫자 토큰)")
    if node == "02":
        if any(w in all_text for w in ("경쟁사 대비", "업계 평균")):
            issues.append("product:comparison")
        if any(w in "\n".join(x["detail"] for x in output["weaknesses"]) for w in ("하지만", "라는 장점", "오히려", "덕분에", "대신")):
            result["manual_review"].append("약점을 긍정 표현으로 회피했는지 확인")
    if node in ("03a", "03b"):
        if output["approach"] != ("차별화" if node == "03a" else "대중 공략"):
            issues.append("strategy:wrong_approach")
        if any(w in p["detail"] for p in output["core_strategies"] for w in ("인지도", "브랜드 가치 제고", "적극 활용")):
            issues.append("strategy:vague_detail")
        if node == "03a" and not any(c["name"] in output["rationale_md"] for c in context["market"]["competitors"]):
            issues.append("strategy:missing_competitor_evidence")
    if node == "04":
        if any(output.get(k, "") != "" for k in ("issued_on", "author_org")):
            issues.append("report:invented_runtime_field")
        if output["brand_name"] != context["brand_name"]:
            issues.append("report:brand_name_changed")
        if len(output["headline"]) > 30 or "!" in output["headline"]:
            issues.append("report:headline")
        if len(set(output["messages"].values())) != 3:
            issues.append("report:duplicate_message")
        if len({x["axis"] for x in output["reasons"]}) != 3:
            issues.append("report:duplicate_axis")
    if node == "05":
        if any(re.search(r"\d[\d,.]*\s*(?:명|회|원|%|퍼센트)", k["target"]) for k in output["kpis"]):
            issues.append("campaign:absolute_kpi")
        if len({p["theme"] for p in output["posts"]}) != len(output["posts"]):
            issues.append("campaign:duplicate_post")
        if any(w in output["objective"] for w in ("인지도", "브랜드 가치 제고")):
            issues.append("campaign:vague_objective")
    if node == "06":
        copies = [c["text"] for c in output["headlines"] + output["body_copies"]]
        tags = [h["tag"] for h in output["hashtags"]]
        if any(w in t for w in FORBIDDEN for t in copies + tags):
            issues.append("copy:forbidden_expression")
        if any(t.count("!") > 1 for t in copies):
            issues.append("copy:exclamations")
        normalized = [re.sub(r"[\W_]", "", t) for t in tags]
        if len(set(normalized)) != len(tags):
            issues.append("copy:duplicate_hashtag")
        if any(not t.startswith("#") for t in tags):
            issues.append("copy:hashtag_prefix")
        if re.sub(r"[\W_]", "", context["brand_name"]) not in normalized:
            issues.append("copy:missing_brand_tag")
        if len({c["text"] for c in output["headlines"]}) != len(output["headlines"]):
            issues.append("copy:duplicate_headline")
    if node == "07":
        campaign, copy = context["campaign"], context["copy"]
        allowed_channels = {c["name"] for c in campaign["channels"]}
        allowed_copy = {c["text"] for c in copy["headlines"] + copy["body_copies"]}
        allowed_tags = {h["tag"] for h in copy["hashtags"]}
        if any(c in all_text and not any(c in a for a in allowed_channels) for c in CHANNELS):
            issues.append("execution:unknown_channel")
        quoted = re.findall(r'"([^"\n]+)"|“([^”\n]+)”|「([^」\n]+)」', all_text)
        if any(next(t for t in group if t) not in allowed_copy for group in quoted):
            issues.append("execution:unknown_quoted_copy")
        if any(t not in allowed_tags for t in re.findall(r"#[\w]+", all_text)):
            issues.append("execution:unknown_hashtag")
        actions = "\n".join(a for p in output["phases"] for a in p["actions"])
        if not any(c["text"] in actions for c in copy["headlines"]):
            issues.append("execution:unused_headline")
        if not any(c in actions for c in allowed_channels):
            issues.append("execution:unused_channel")
        if re.search(r"\d[\d,.]*\s*(?:만원|억원|원)", all_text):
            issues.append("execution:invented_amount")
    return result


def build_prompt(node, context):
    validate_context(node, context)
    path = AI / "prompts" / "brand" / (NODES[node][0] + ".md")
    raw = path.read_text(encoding="utf-8")
    blocks = dict(re.findall(r"<!-- prompt:(\w+) -->\s*```[^\n]*\n(.*?)```", raw, re.S))
    if not {"system", "user"} <= blocks.keys():
        raise ValueError("프롬프트 마커 누락")
    values = {"brand_strategy": context} if node in ("05", "06") else context
    def replace(match):
        key = match[1]
        if key == "comment" and key not in values:
            return "없음"
        if key not in values:
            raise ValueError(f"프롬프트 입력 누락: {key}")
        value = values[key]
        return json.dumps(value, ensure_ascii=False) if isinstance(value, (dict, list)) else str(value or "없음")
    user = re.sub(r"\{(\w+)\}", replace, blocks["user"])
    return blocks["system"], user + "\n\n" + blocks.get("fewshot", "")


def summarize(node, records, expected_level=None):
    count = len(records)
    schema_pass = sum(r["schema_pass"] for r in records)
    auto_pass = sum(not r["issues"] for r in records)
    levels = Counter(r.get("competition_level") for r in records if r.get("competition_level") in ("high", "low"))
    stability = None
    if node == "01":
        stability = {"counts": dict(levels), "required_runs": 10,
                     "pass": count >= 10 and sum(levels.values()) == count and len(levels) == 1
                     and (expected_level is None or set(levels) == {expected_level})}
    return {"node": node, "runs": count, "schema_pass_count": schema_pass,
            "schema_pass_rate": schema_pass / count if count else 0,
            "auto_pass_count": auto_pass, "auto_pass_rate": auto_pass / count if count else 0,
            "schema_target_met": count > 0 and schema_pass / count >= .95,
            "competition_stability": stability, "manual_review_complete": False,
            "issue_counts": dict(Counter(i for r in records for i in r["issues"])), "records": records}


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--node", choices=NODES, required=True)
    source = ap.add_mutually_exclusive_group(required=True)
    source.add_argument("--brief", type=Path, help="01/02용 브리프")
    source.add_argument("--input", type=Path, help="노드 입력 JSON (README 참고)")
    mode = ap.add_mutually_exclusive_group(required=True)
    mode.add_argument("--file", type=Path, help="Runtime 주입 전 원본 payload 검사")
    mode.add_argument("--runs", type=int)
    ap.add_argument("--expected-level", choices=("high", "low"))
    ap.add_argument("--env-file", type=Path, default=RUNTIME / ".env")
    ap.add_argument("--report", type=Path)
    args = ap.parse_args(argv)
    if args.brief and args.node not in ("01", "02"):
        ap.error("--brief는 01/02에만 사용합니다. 다른 노드는 --input을 사용하세요")
    if args.runs is not None and args.runs < 1:
        ap.error("--runs는 양수여야 합니다")
    if args.expected_level and args.node != "01":
        ap.error("--expected-level은 01에만 사용합니다")
    context = read_json(args.brief or args.input)
    validate_context(args.node, context)
    records = []
    if args.file:
        output = read_json(args.file)
        row = check(args.node, output, context, args.expected_level)
        if isinstance(output, dict):
            row["competition_level"] = output.get("competition_level")
        records.append(row)
    else:
        from dotenv import load_dotenv
        load_dotenv(args.env_file, override=False)
        if not (os.getenv("LLM_API_KEY") or os.getenv("GEMINI_API_KEY")):
            raise ValueError("LLM_API_KEY 미설정. 로컬 환경에 설정한 뒤 실행하세요 (값을 출력하지 않습니다)")
        sys.path.insert(0, str(RUNTIME))
        from src import catalog
        from src.llm import LLM, DEFAULT_BASE_URL
        if os.getenv("LLM_BASE_URL", DEFAULT_BASE_URL).rstrip("/") != DEFAULT_BASE_URL:
            raise ValueError("이 평가기는 GMS 경유만 사용합니다")
        model = os.getenv("LLM_MODEL") or os.getenv("GEMINI_MODEL", "gemini-2.5-flash")
        if model != "gemini-2.5-flash":
            raise ValueError("이 평가기는 gemini-2.5-flash만 사용합니다")
        system, user = build_prompt(args.node, context)
        llm = LLM(model=model)
        response_schema = catalog.response_schema(f"biz.{NODES[args.node][1]}@1")
        run_dir = ROOT / "outputs" / ("brand-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ"))
        run_dir.mkdir(parents=True)
        for index in range(args.runs):
            try:
                response = llm.generate(system=system, user=user, label=args.node, response_schema=response_schema)
            except Exception as exc:
                # SDK 예외 원문에는 요청 정보가 포함될 수 있다.
                records.append({"schema_pass": False, "issues": ["api:" + type(exc).__name__], "manual_review": []})
                print(f"{index + 1}/{args.runs}: API 오류 (세부 요청은 기록하지 않음)", file=sys.stderr)
                continue
            try:
                output = json.loads(response.text)
            except (ValueError, TypeError):
                row = {"schema_pass": False, "issues": ["schema:invalid_json"], "manual_review": []}
            else:
                row = check(args.node, output, context, args.expected_level)
                if isinstance(output, dict):
                    row["competition_level"] = output.get("competition_level")
                (run_dir / f"{args.node}-{index + 1:03}.json").write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")
            row.update(input_tokens=response.input_tokens, output_tokens=response.output_tokens,
                       schema_mode=response.schema_mode, retries=response.retries)
            records.append(row)
            print(f"{index + 1}/{args.runs}: {'통과' if not row['issues'] else '실패'}", file=sys.stderr)
        if args.report is None:
            args.report = run_dir / "report.json"
    report = summarize(args.node, records, args.expected_level)
    report["mode"] = "offline" if args.file else "live_raw_response"
    report["input_sha256"] = hashlib.sha256(json.dumps(context, ensure_ascii=False, sort_keys=True).encode()).hexdigest()
    report["prompt_sha256"] = hashlib.sha256((AI / "prompts" / "brand" / (NODES[args.node][0] + ".md")).read_bytes()).hexdigest()
    encoded = json.dumps(report, ensure_ascii=False, indent=2)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(encoded, encoding="utf-8")
    print(encoded)
    stability = report["competition_stability"]
    return 0 if report["auto_pass_rate"] >= .95 and (stability is None or args.file or stability["pass"]) else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, OSError) as exc:
        print(f"평가 설정/입력 오류: {exc}", file=sys.stderr)
        raise SystemExit(2)
