"""Playwright JSON 결과(e2e/test-results/results.json)를 읽어 테스트별 결과, 실패 원인, 스크린샷 경로를 정리한다.

    python .claude/skills/tdd-pipeline/scripts/e2e_results.py ../../e2e/test-results/results.json

스크린샷 경로는 최종 보고에 그대로 붙이고, 에이전트는 그 파일을 열어 화면을 직접 확인한다.
"""
import argparse
import json
import sys
from pathlib import Path


def walk(suite, file=None):
    file = suite.get("file") or file

    for spec in suite.get("specs", []):
        for test in spec.get("tests", []):
            yield file, spec, test

    for child in suite.get("suites", []):
        yield from walk(child, file)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("results", help="e2e/test-results/results.json")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()

    results_path = Path(args.results)
    data = json.loads(results_path.read_text(encoding="utf-8"))
    e2e_root = results_path.resolve().parents[1]
    rows = []

    for suite in data.get("suites", []):
        for file, spec, test in walk(suite):
            last = (test.get("results") or [{}])[-1]
            errors = [e.get("message", "") for e in last.get("errors", [])]
            shots = [
                Path(a["path"]).resolve().relative_to(e2e_root).as_posix()
                for a in last.get("attachments", [])
                if a.get("contentType") == "image/png" and a.get("path")
            ]
            rows.append({
                "project": test.get("projectName"),
                "file": file,
                "title": spec.get("title"),
                # expected · unexpected · flaky · skipped
                "status": test.get("status"),
                "durationMs": last.get("duration"),
                "errors": [e.splitlines()[0][:300] if e else "" for e in errors],
                "screenshots": shots,
            })

    if args.json:
        json.dump({"stats": data.get("stats"), "tests": rows}, sys.stdout, ensure_ascii=False, indent=2)
        print()
        return

    stats = data.get("stats", {})
    print(f"통과 {stats.get('expected', 0)} · 실패 {stats.get('unexpected', 0)} · "
          f"불안정 {stats.get('flaky', 0)} · 건너뜀 {stats.get('skipped', 0)}")

    for r in rows:
        mark = {"expected": "ok", "unexpected": "FAIL", "flaky": "FLAKY", "skipped": "skip"}.get(r["status"], r["status"])
        print(f"- [{mark}] [{r['project']}] {r['file']} › {r['title']}")
        for e in r["errors"]:
            print(f"    오류: {e}")
        for s in r["screenshots"]:
            print(f"    화면: e2e/{s}")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
