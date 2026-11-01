"""Gradle 테스트 결과 XML 을 읽어 테스트별 통과·실패와 실패 원인을 정리한다. RED · GREEN 판정에 쓴다.

백엔드 루트에서 실행한다. 테스트를 돌린 직후에 읽는다 — 결과 폴더에는 마지막 실행분만 남는다.

    python .claude/skills/tdd-pipeline/scripts/test_results.py core/core-api
    python .claude/skills/tdd-pipeline/scripts/test_results.py core/core-api --task integrationTest --classes com.x.FooTest

실패 원인(type)으로 RED 가 제대로인지 본다.
- java.lang.UnsupportedOperationException: 스텁이 던진 것 — 아직 구현이 없어서 실패한 정상 RED
- org.opentest4j.AssertionFailedError / java.lang.AssertionError: 단언이 틀린 것 — 정상 RED
- 그 밖(NullPointerException, 스프링 컨텍스트 기동 실패 등): 테스트 준비가 잘못됐을 수 있다 — 확인이 필요하다
"""
import argparse
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

EXPECTED_RED = {
    "java.lang.UnsupportedOperationException",
    "org.opentest4j.AssertionFailedError",
    "java.lang.AssertionError",
    "org.opentest4j.MultipleFailuresError",
}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("module_dir", help="예: core/core-api")
    parser.add_argument("--task", default="test", help="test · integrationTest")
    parser.add_argument("--classes", nargs="*", default=[], help="이 테스트 클래스(FQCN)만 본다")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()

    results_dir = Path(args.module_dir) / "build" / "test-results" / args.task

    if not results_dir.is_dir():
        print(f"결과 폴더가 없다: {results_dir} — 테스트가 컴파일되지 않았거나 돌지 않았다.")
        sys.exit(2)

    cases = []

    for report in sorted(results_dir.glob("TEST-*.xml")):
        for case in ET.parse(report).getroot().iter("testcase"):
            classname = case.get("classname")

            if args.classes and classname not in args.classes:
                continue

            failure = case.find("failure")
            if failure is None:
                failure = case.find("error")

            status = "passed"
            if case.find("skipped") is not None:
                status = "skipped"
            elif failure is not None:
                status = "failed"

            cases.append({
                "class": classname,
                "test": case.get("name"),
                "status": status,
                "failureType": failure.get("type") if failure is not None else None,
                "message": (failure.get("message") or "")[:300] if failure is not None else None,
            })

    if args.json:
        json.dump(cases, sys.stdout, ensure_ascii=False, indent=2)
        print()
        return

    counts = {s: sum(c["status"] == s for c in cases) for s in ("passed", "failed", "skipped")}
    print(f"통과 {counts['passed']} · 실패 {counts['failed']} · 건너뜀 {counts['skipped']}")

    for c in cases:
        if c["status"] != "failed":
            continue

        note = "" if c["failureType"] in EXPECTED_RED else "  ← 정상 RED 가 아닐 수 있다"
        print(f"- {c['class']}#{c['test']}: {c['failureType']}{note}")
        if c["message"]:
            print(f"    {c['message']}")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
