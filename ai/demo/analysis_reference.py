"""
성적 분석 프롬프트 채점용 — 정답 계산기 (S15P21A403-271 / -272)

★ 이것은 분석 노드의 구현이 아니다.
  분석 노드는 **LLM 노드**로 간다. 판매자가 프롬프트를 등록하는 상품이어야
  마켓플레이스의 정산이 성립하기 때문이다 (트랙 A 결정, 09-17).
  이 파일은 파이프라인에 들어가지 않는다.

그럼 왜 필요한가.
  분석 프롬프트의 출력은 문장이 아니라 **숫자**다. 학생 20명 × 과목 4개 =
  80개 값을 평균 내고 빼는 일을 LLM이 한다. 스키마를 통과했다고 값이 맞는 건
  아니므로, **맞는 값이 무엇인지 아는 쪽**이 따로 있어야 채점이 된다.
  그게 이 파일이다.

  LLM 노드로 간다는 결정 때문에 이 파일은 없어진 게 아니라 오히려 필수가 됐다.

쓰이는 곳
  ai/demo/run_golden_cases.py   골든 케이스 기대값 + 구현 출력 대조
  ai/prompts/analysis_node.md   프롬프트가 지시하는 계산 규칙의 원본

★ 계산 규칙은 `ai/prompts/analysis_node.md` 의 System 프롬프트와
  **한 글자도 다르면 안 된다.** 두 곳에 규칙이 흩어지면 "프롬프트는 이렇게
  시켰는데 채점은 저렇게 한" 상태가 되어 준수율 숫자가 거짓말이 된다.

실행:
    cd ai/demo
    python analysis_reference.py                    # 데모 데이터 2벌의 정답 계산
    python analysis_reference.py --check out.json   # LLM 출력과 대조
"""

import argparse
import json
from pathlib import Path

DATA_DIR = Path(__file__).resolve().parent / "data"

TREND_UP = 1.0          # avg_delta 가 이 이상이면 "up"
TREND_DOWN = -1.0       # 이 이하면 "down". 사이는 "flat"
TOLERANCE = 0.01        # 대조 시 허용 오차 (반올림 자리)


def r2(x: float) -> float:
    """소수 둘째 자리 반올림. 프롬프트의 규칙과 동일하다."""
    return round(x + 0.0, 2)


def analyze(roster: dict) -> dict:
    """edu.roster@1 → edu.analysis@1."""
    subjects = roster["subjects"]
    students = roster["students"]

    out_students = []
    for s in students:
        cur = [s["scores"][sub]["current"] for sub in subjects]
        prev = [s["scores"][sub]["previous"] for sub in subjects]
        avg_cur = sum(cur) / len(cur)
        delta = avg_cur - sum(prev) / len(prev)

        # 동점이면 subjects 배열에서 먼저 나오는 과목 — min() 이 그렇게 동작한다
        weak = min(subjects, key=lambda sub: s["scores"][sub]["current"])

        trend = "up" if delta >= TREND_UP else ("down" if delta <= TREND_DOWN else "flat")

        out_students.append({
            "name": s["name"],
            "avg_current": r2(avg_cur),
            "avg_delta": r2(delta),
            "weak_subject": weak,
            "trend": trend,
        })

    out_subjects = []
    for sub in subjects:
        cur = [s["scores"][sub]["current"] for s in students]
        prev = [s["scores"][sub]["previous"] for s in students]
        out_subjects.append({
            "name": sub,
            "avg_current": r2(sum(cur) / len(cur)),
            "avg_delta": r2(sum(cur) / len(cur) - sum(prev) / len(prev)),
        })

    # ★ 전체 평균은 "학생 평균의 평균"이 아니라 "모든 점수의 평균"이다.
    #   과목 수가 학생마다 같으므로 결과는 같지만, 규칙을 프롬프트와 맞춰 둔다.
    all_cur = [s["scores"][sub]["current"] for s in students for sub in subjects]
    all_prev = [s["scores"][sub]["previous"] for s in students for sub in subjects]
    avg_cur, avg_prev = sum(all_cur) / len(all_cur), sum(all_prev) / len(all_prev)

    return {
        "summary": {
            "student_count": len(students),
            "avg_current": r2(avg_cur),
            "avg_previous": r2(avg_prev),
            "avg_delta": r2(avg_cur - avg_prev),
        },
        "students": out_students,
        "subjects": out_subjects,
    }


def compare(expected: dict, actual: dict) -> list[str]:
    """LLM 출력(actual)이 기준값(expected)과 맞는지 본다. 어긋난 항목을 돌려준다."""
    problems = []

    for key, want in expected["summary"].items():
        got = actual.get("summary", {}).get(key)
        if got is None:
            problems.append(f"summary.{key} 없음")
        elif abs(float(got) - float(want)) > TOLERANCE:
            problems.append(f"summary.{key}  기대 {want} / 실제 {got}")

    exp_students = {s["name"]: s for s in expected["students"]}
    act_students = {s.get("name"): s for s in actual.get("students", [])}

    missing = set(exp_students) - set(act_students)
    invented = set(act_students) - set(exp_students)
    if missing:
        problems.append(f"빠진 학생 {len(missing)}명: {sorted(missing)[:3]}")
    if invented:
        problems.append(f"없는 학생을 만들어냄: {sorted(invented)[:3]}")

    for name in sorted(set(exp_students) & set(act_students)):
        want, got = exp_students[name], act_students[name]
        for key in ("avg_current", "avg_delta"):
            if abs(float(got.get(key, 0)) - want[key]) > TOLERANCE:
                problems.append(f"{name}.{key}  기대 {want[key]} / 실제 {got.get(key)}")
        for key in ("weak_subject", "trend"):
            if got.get(key) != want[key]:
                problems.append(f"{name}.{key}  기대 {want[key]} / 실제 {got.get(key)}")

    return problems


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--roster", default=None, help="입력 roster JSON (기본: 데모 2벌)")
    ap.add_argument("--check", default=None, help="대조할 LLM 출력 JSON")
    args = ap.parse_args()

    if args.check:
        roster_path = Path(args.roster or DATA_DIR / "demo_roster_down.json")
        expected = analyze(json.loads(roster_path.read_text(encoding="utf-8")))
        actual = json.loads(Path(args.check).read_text(encoding="utf-8"))
        problems = compare(expected, actual)
        if problems:
            print(f"수치 정합성 실패 — {len(problems)}건")
            for p in problems[:15]:
                print(f"  · {p}")
            raise SystemExit(1)
        print("수치 정합성 통과 — 모든 값이 기준과 일치한다")
        return

    targets = [Path(args.roster)] if args.roster else [
        DATA_DIR / "demo_roster_down.json", DATA_DIR / "demo_roster_up.json"
    ]
    for path in targets:
        result = analyze(json.loads(path.read_text(encoding="utf-8")))
        s = result["summary"]
        branch = "경고형" if s["avg_delta"] < 0 else "격려형"
        print(f"\n── {path.name} ──")
        print(f"  학생 {s['student_count']}명 · 평균 {s['avg_previous']} → {s['avg_current']} "
              f"({s['avg_delta']:+.2f}) → {branch} 분기")
        print("  과목별: " + " · ".join(
            f"{x['name']} {x['avg_current']}({x['avg_delta']:+.2f})" for x in result["subjects"]))
        trends = {}
        for x in result["students"]:
            trends[x["trend"]] = trends.get(x["trend"], 0) + 1
        print(f"  학생 추세: " + " · ".join(f"{k} {v}명" for k, v in sorted(trends.items())))

        out = path.with_name(path.name.replace("roster", "analysis"))
        out.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"  → {out.name}")


if __name__ == "__main__":
    main()
