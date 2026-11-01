"""PIT 의 mutations.xml 을 읽어 점수와 살아남은 돌연변이를 정리한다.

백엔드 루트에서 실행한다.

    python .claude/skills/tdd-pipeline/scripts/pit_summary.py \
        --report core/core-api/build/reports/pitest/mutations.xml \
        --classes com.aiblockclock.domain.Foo com.aiblockclock.domain.Bar \
        --threshold 80

--classes 를 주면 그 클래스(와 내부 클래스)의 돌연변이만 센다. 모듈 전체를 돌린 리포트에서 이번 변경분만 보려는 것이다.
점수 = 잡힌 돌연변이 / 전체 돌연변이. 테스트가 아예 닿지 않은 것(NO_COVERAGE)도 못 잡은 것으로 센다.
"""
import argparse
import json
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path


def short_mutator(name):
    return name.rsplit(".", 1)[-1].removesuffix("Mutator")


def source_line(module_dir, mutated_class, source_file, line_number):
    package = mutated_class.split("$")[0].rsplit(".", 1)[0]
    path = module_dir / "src" / "main" / "java" / Path(*package.split(".")) / source_file

    try:
        lines = path.read_text(encoding="utf-8").splitlines()
        return lines[line_number - 1].strip()
    except (OSError, IndexError):
        return ""


def in_scope(mutated_class, classes):
    if not classes:
        return True

    outer = mutated_class.split("$")[0]
    return outer in classes


def load(report, classes):
    # <모듈>/build/reports/pitest/mutations.xml
    module_dir = Path(report).resolve().parents[3]
    mutations = []

    for node in ET.parse(report).getroot().iter("mutation"):
        mutated_class = node.findtext("mutatedClass")

        if not in_scope(mutated_class, classes):
            continue

        line_number = int(node.findtext("lineNumber"))
        source_file = node.findtext("sourceFile")

        mutations.append({
            "status": node.get("status"),
            "detected": node.get("detected") == "true",
            "class": mutated_class,
            "method": node.findtext("mutatedMethod"),
            "line": line_number,
            "sourceFile": source_file,
            "mutator": short_mutator(node.findtext("mutator")),
            "description": node.findtext("description"),
            "killingTest": node.findtext("killingTest") or None,
            "code": source_line(module_dir, mutated_class, source_file, line_number),
        })

    return mutations


def score(mutations):
    if not mutations:
        return None

    return round(100 * sum(m["detected"] for m in mutations) / len(mutations), 1)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", action="append", required=True, help="mutations.xml 경로. 여러 번 줄 수 있다")
    parser.add_argument("--classes", nargs="*", default=[], help="이 클래스(FQCN)만 센다")
    parser.add_argument("--threshold", type=float, default=80.0)
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()

    classes = set(args.classes)
    mutations = [m for report in args.report for m in load(report, classes)]
    survivors = [m for m in mutations if not m["detected"]]
    by_class = defaultdict(list)

    for m in mutations:
        by_class[m["class"].split("$")[0]].append(m)

    total = score(mutations)
    result = {
        "score": total,
        "threshold": args.threshold,
        "passed": total is not None and total >= args.threshold,
        "total": len(mutations),
        "killed": len(mutations) - len(survivors),
        "perClass": {c: {"score": score(ms), "total": len(ms)} for c, ms in sorted(by_class.items())},
        "survivors": survivors,
    }

    if args.json:
        json.dump(result, sys.stdout, ensure_ascii=False, indent=2)
        print()
        return

    if total is None:
        print("대상 돌연변이가 없다. --classes 가 맞는지, PIT 를 그 범위로 돌렸는지 확인한다.")
        return

    verdict = "PASS" if result["passed"] else "FAIL"
    print(f"# 돌연변이 점수 {total}% ({result['killed']}/{result['total']}) — 기준 {args.threshold}% {verdict}")
    print()
    print("| 클래스 | 점수 | 돌연변이 |")
    print("| --- | --- | --- |")
    for c, s in result["perClass"].items():
        print(f"| `{c}` | {s['score']}% | {s['total']} |")

    if survivors:
        print()
        print("## 살아남은 돌연변이")
        print()
        print("| # | 위치 | 메서드 | 상태 | 변형 | 코드 |")
        print("| --- | --- | --- | --- | --- | --- |")
        for i, m in enumerate(survivors, 1):
            code = m["code"].replace("|", "\\|")
            print(f"| {i} | `{m['sourceFile']}:{m['line']}` | `{m['method']}` | {m['status']} "
                  f"| {m['description']} | `{code}` |")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
