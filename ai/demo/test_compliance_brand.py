"""네트워크 없이 정상/오류 payload 및 집계의 회귀를 검증한다."""
from copy import deepcopy
import contextlib
import io
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

import check_compliance_brand as c


def fixture(kind):
    """카탈로그 구조에서 최소 유효 객체 생성; 내용은 아래 정상 사례로 지정한다."""
    spec = c.schema(kind)
    def make(s):
        if "$ref" in s:
            return make(spec["$defs"][s["$ref"].split("/")[-1]])
        if "anyOf" in s:
            return make(s["anyOf"][0])
        if "enum" in s:
            return s["enum"][0]
        if s["type"] == "object":
            return {k: make(v) for k, v in s["properties"].items()}
        if s["type"] == "array":
            return [make(s["items"]) for _ in range(s.get("minItems", 1))]
        return "내용"
    return make(spec)


class ComplianceTests(unittest.TestCase):
    def setUp(self):
        self.brief = c.read_json(c.ROOT / "brand" / "brief_high.json")
        self.market = fixture("market_research")
        self.market.update(competition_level="high", level_reason="전국 유통 브랜드 5개를 확인했다.")
        self.product = fixture("product_analysis")
        self.draft = fixture("strategy_draft")
        self.draft.update(approach="차별화", rationale_md="내용을 바탕으로 교체 경험을 강조한다.")
        self.report = fixture("brand_strategy")
        self.report.update(brand_name=self.brief["brand_name"], issued_on="", author_org="",
                           messages={"core": "편안한 교체", "sub": "대나무 손잡이", "daily": "오늘도 개운하게"})
        for row, axis in zip(self.report["reasons"], ("기능", "메시지", "제품")):
            row["axis"] = axis
        self.campaign = fixture("campaign_plan")
        self.campaign["channels"] = [{"name": "인스타그램", "reason": "교체 경험을 이미지로 전달"}]
        for row, theme in zip(self.campaign["posts"], ("소개", "사용", "교체")):
            row["theme"] = theme
        self.copy = fixture("ad_copy")
        self.copy["headlines"] = [{"text": t} for t in ("편안한 교체", "매일 개운하게", "손에 닿는 대나무")]
        self.copy["hashtags"] = [{"tag": t} for t in ("#소소칫솔", "#칫솔", "#교체")]
        self.execution = fixture("execution_plan")
        for phase in self.execution["phases"]:
            phase["actions"] = ['인스타그램에 "편안한 교체" 게시', "#소소칫솔 태그 추가"]
        self.contexts = {"01": self.brief, "02": self.brief,
                         "03a": {"market": self.market, "product": self.product},
                         "03b": {"market": dict(self.market, competition_level="low"), "product": self.product},
                         "04": {"brand_name": self.brief["brand_name"], "strategy_draft": self.draft},
                         "05": self.report, "06": self.report,
                         "07": {"campaign": self.campaign, "copy": self.copy}}
        self.outputs = {"01": self.market, "02": self.product, "03a": self.draft,
                        "03b": dict(self.draft, approach="대중 공략"), "04": self.report,
                        "05": self.campaign, "06": self.copy, "07": self.execution}

    def check(self, node, output):
        return c.check(node, output, self.contexts[node])

    def test_all_normal_outputs_and_prompt_slots(self):
        for node, output in self.outputs.items():
            with self.subTest(node=node):
                self.assertEqual(self.check(node, output)["issues"], [])
                system, user = c.build_prompt(node, self.contexts[node])
                self.assertTrue(system and user)
                self.assertNotRegex(user, r"\{(?:brand_name|market|product|strategy_draft|campaign|copy|brand_strategy)\}")

    def test_report_array_count_and_runtime_fields(self):
        for key in ("purpose", "positioning", "reasons", "core_strategy"):
            with self.subTest(key=key):
                bad = deepcopy(self.report)
                bad[key].pop()
                self.assertFalse(self.check("04", bad)["schema_pass"])
        for key in ("issued_on", "author_org"):
            bad = dict(self.report, **{key: "2026-09-20"})
            self.assertIn("report:invented_runtime_field", self.check("04", bad)["issues"])
        omitted = {k: v for k, v in self.report.items() if k not in ("issued_on", "author_org")}
        self.assertEqual(self.check("04", omitted)["issues"], [])

    def test_wrong_branch_marker_and_input(self):
        for node, wrong in (("03a", "대중 공략"), ("03b", "차별화")):
            self.assertIn("strategy:wrong_approach", self.check(node, dict(self.draft, approach=wrong))["issues"])
        with self.assertRaises(ValueError):
            c.validate_context("03b", self.contexts["03a"])

    def test_market_boundary_and_expected_level(self):
        for count, level in ((4, "low"), (5, "high")):
            good = dict(self.market, competition_level=level, level_reason=f"전국 유통 브랜드 {count}개를 확인했다.")
            self.assertEqual(self.check("01", good)["issues"], [])
            bad = dict(good, competition_level="low" if level == "high" else "high")
            self.assertIn("market:reason_level_mismatch", self.check("01", bad)["issues"])
        self.assertIn("market:unexpected_level", c.check("01", self.market, self.brief, "low")["issues"])

    def test_execution_unknown_channel_copy_tag_and_unused_copy(self):
        for action, issue in (("틱톡에 게시", "execution:unknown_channel"),
                              ('인스타그램에 "새 카피" 게시', "execution:unknown_quoted_copy"),
                              ("#새태그 추가", "execution:unknown_hashtag")):
            bad = deepcopy(self.execution)
            bad["phases"][0]["actions"].append(action)
            self.assertIn(issue, self.check("07", bad)["issues"])
        bad = deepcopy(self.execution)
        for phase in bad["phases"]:
            phase["actions"] = ["인스타그램에 게시", "#소소칫솔 태그 추가"]
        self.assertIn("execution:unused_headline", self.check("07", bad)["issues"])

    def test_kpi_relative_counts_and_absolute_counts(self):
        for target in ("게시물 6개 중 4개 이상에서 발생", "캠페인 종료 시 전월 대비 증가"):
            good = deepcopy(self.campaign)
            good["kpis"][0]["target"] = target
            self.assertEqual(self.check("05", good)["issues"], [])
        bad = deepcopy(self.campaign)
        bad["kpis"][0]["target"] = "팔로워 5,000명"
        self.assertIn("campaign:absolute_kpi", self.check("05", bad)["issues"])

    def test_ad_objects_and_forbidden_expression(self):
        bad = deepcopy(self.copy)
        bad["headlines"][0]["text"] = "최고의 칫솔"
        self.assertIn("copy:forbidden_expression", self.check("06", bad)["issues"])
        bad = deepcopy(self.copy)
        bad["headlines"] = ["a", "b", "c"]
        self.assertFalse(self.check("06", bad)["schema_pass"])

    def test_malformed_types_never_crash_content_checks(self):
        for node, output in self.outputs.items():
            for bad in (None, [], "문자열", {}, {k: None for k in output}):
                with self.subTest(node=node, bad=bad):
                    self.assertFalse(self.check(node, bad)["schema_pass"])

    def test_missing_comparison_input_is_not_a_pass(self):
        for node in c.NODES:
            with self.assertRaises(ValueError):
                c.check(node, self.outputs[node], {})

    def test_stability_requires_ten_complete_runs(self):
        good = dict(schema_pass=True, issues=[], competition_level="high")
        failed = dict(schema_pass=False, issues=["api:TimeoutError"])
        self.assertFalse(c.summarize("01", [good] * 9)["competition_stability"]["pass"])
        self.assertTrue(c.summarize("01", [good] * 10, "high")["competition_stability"]["pass"])
        for rows in ([good] * 9 + [failed], [good] * 9 + [dict(good, competition_level="low")]):
            self.assertFalse(c.summarize("01", rows)["competition_stability"]["pass"])
        self.assertFalse(c.summarize("01", [good] * 10, "low")["competition_stability"]["pass"])

    def test_denominator_and_manual_review(self):
        rows = [dict(schema_pass=True, issues=[])] * 19 + [dict(schema_pass=False, issues=["api:TimeoutError"])]
        report = c.summarize("02", rows)
        self.assertEqual(report["schema_pass_rate"], .95)
        self.assertTrue(report["schema_target_met"])
        self.assertFalse(report["manual_review_complete"])

    def test_cli_offline_exit_and_report(self):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            inp, out, report = (base / n for n in ("input.json", "output.json", "report.json"))
            inp.write_text(json.dumps(self.contexts["04"]), encoding="utf-8")
            args = ["--node", "04", "--input", str(inp), "--file", str(out), "--report", str(report)]
            for payload, code in ((self.report, 0), (dict(self.report, issued_on="2026-09-20"), 1)):
                out.write_text(json.dumps(payload), encoding="utf-8")
                with contextlib.redirect_stdout(io.StringIO()):
                    self.assertEqual(c.main(args), code)
                self.assertEqual(c.read_json(report)["mode"], "offline")

    def test_live_path_with_fake_adapter_counts_api_and_json_failures(self):
        response = lambda text: SimpleNamespace(text=text, input_tokens=12, output_tokens=8,
                                               schema_mode="response_schema", retries=0)
        llm = Mock()
        llm.generate.side_effect = [response(json.dumps(self.market)), response("invalid"), TimeoutError("private request")]
        fake_catalog = SimpleNamespace(response_schema=Mock(return_value={"type": "object"}))
        fake_llm = SimpleNamespace(LLM=Mock(return_value=llm), DEFAULT_BASE_URL="https://gms.ssafy.io/gmsapi/generativelanguage.googleapis.com")
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            inp = base / "input.json"
            inp.write_text(json.dumps(self.brief), encoding="utf-8")
            modules = {"src": SimpleNamespace(catalog=fake_catalog), "src.llm": fake_llm}
            with patch.dict("sys.modules", modules), patch.dict("os.environ", {"LLM_API_KEY": "test-placeholder"}, clear=True), patch.object(c, "ROOT", base):
                stdout, stderr = io.StringIO(), io.StringIO()
                with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
                    code = c.main(["--node", "01", "--brief", str(inp), "--runs", "3", "--env-file", str(base / "absent.env")])
                report = json.loads(stdout.getvalue())
                self.assertEqual(code, 1)
                self.assertEqual(report["schema_pass_rate"], 1 / 3)
                self.assertEqual(report["issue_counts"], {"schema:invalid_json": 1, "api:TimeoutError": 1})
                self.assertNotIn("private request", stdout.getvalue() + stderr.getvalue())
                self.assertEqual(len(list((base / "outputs").glob("*/report.json"))), 1)


if __name__ == "__main__":
    unittest.main()
