"""
골든 케이스 실행 + 원본 엑셀 수치 정합성 검증 (S15P21A403-272)

두 가지를 한 번에 한다.

  ① 골든 케이스 검증
     케이스마다 적어둔 기대 조건과 분석 결과가 맞는지 본다.
     분석 노드를 고칠 때마다 이걸 돌려 예전에 맞던 게 여전히 맞는지 확인한다.

  ② 원본 엑셀 대조 (AI-08-5)
     ★ 여기가 핵심이다. 기대값을 analysis_reference.py 로 만들고
       검증도 analysis_reference.py 로 하면 아무것도 검증하지 못한다.
       같은 코드가 같은 답을 내는 걸 확인할 뿐이다.

       그래서 이 검사는 **엑셀 셀에서 직접 다시 계산한다.** JSON 을 거치지 않고,
       analyze() 를 쓰지 않고, 파이썬 기본 연산만으로 평균을 낸다.
       두 경로가 독립적이어야 한쪽의 실수가 드러난다.

실행:
    cd ai/demo
    python run_golden_cases.py                       # 레퍼런스 구현 검증
    python run_golden_cases.py --impl out.json --case G1   # 다른 구현의 출력 검증
"""

import argparse
import json
import sys
from pathlib import Path

from analysis_reference import analyze, compare

BASE_DIR = Path(__file__).resolve().parent
CASES_FILE = BASE_DIR / "golden" / "cases.json"
TOLERANCE = 0.01


def load_cases() -> list[dict]:
    if not CASES_FILE.exists():
        raise SystemExit("golden/cases.json 이 없습니다. 먼저 make_golden_cases.py 를 실행하세요.")
    return json.loads(CASES_FILE.read_text(encoding="utf-8"))["cases"]


def excel_totals(xlsx: Path) -> dict | None:
    """엑셀 셀에서 직접 합계를 낸다. JSON 도 analyze() 도 거치지 않는다.

    열 이름 규칙: `과목(이번달)` · `과목(지난달)`.
    데이터 사전(data/README.md)에 적힌 그대로다.

    ★ openpyxl 이 없으면 **None** 을 돌려준다. 빈 dict 가 아니다.
      "대조했는데 문제가 없었다"와 "대조를 못 했다"는 전혀 다른 상태인데,
      둘 다 falsy 라서 한 번 섞이면 구분이 안 된다.
      실제로 첫 버전이 이 구분을 못 해서, 대조를 한 번도 안 하고
      "엑셀 계산값과 일치한다"고 단언했다.
    """
    try:
        from openpyxl import load_workbook
    except ImportError:
        return None

    ws = load_workbook(xlsx, data_only=True).active
    rows = list(ws.iter_rows(values_only=True))
    header = [str(h) if h is not None else "" for h in rows[0]]

    cur_cols = [i for i, h in enumerate(header) if h.endswith("(이번달)")]
    prev_cols = [i for i, h in enumerate(header) if h.endswith("(지난달)")]
    if not cur_cols or len(cur_cols) != len(prev_cols):
        raise SystemExit(f"{xlsx.name}: 이번달/지난달 열을 찾지 못했습니다 — {header}")

    cur_sum = prev_sum = 0
    n_students = 0
    for row in rows[1:]:
        if row[0] in (None, ""):
            continue
        n_students += 1
        cur_sum += sum(row[i] for i in cur_cols)
        prev_sum += sum(row[i] for i in prev_cols)

    n_scores = n_students * len(cur_cols)
    return {
        "student_count": n_students,
        "avg_current": round(cur_sum / n_scores, 2),
        "avg_previous": round(prev_sum / n_scores, 2),
        "avg_delta": round(cur_sum / n_scores - prev_sum / n_scores, 2),
    }


def check_expectations(case: dict, result: dict) -> list[str]:
    exp = case["expect"]
    problems = []
    summary = result["summary"]

    if "student_count" in exp and summary["student_count"] != exp["student_count"]:
        problems.append(f"student_count 기대 {exp['student_count']} / 실제 {summary['student_count']}")

    if "avg_delta" in exp and abs(summary["avg_delta"] - exp["avg_delta"]) > TOLERANCE:
        problems.append(f"avg_delta 기대 {exp['avg_delta']} / 실제 {summary['avg_delta']}")

    if "branch" in exp:
        got = "warn" if summary["avg_delta"] < 0 else "encourage"
        if got != exp["branch"]:
            problems.append(
                f"분기 기대 {exp['branch']} / 실제 {got}  (avg_delta={summary['avg_delta']})")

    if "trend_counts" in exp:
        counts = {}
        for s in result["students"]:
            counts[s["trend"]] = counts.get(s["trend"], 0) + 1
        for trend, want in exp["trend_counts"].items():
            if counts.get(trend, 0) != want:
                problems.append(f"trend={trend} 기대 {want}명 / 실제 {counts.get(trend, 0)}명")

    by_name = {s["name"]: s for s in result["students"]}
    for name, wants in exp.get("students", {}).items():
        got = by_name.get(name)
        if got is None:
            problems.append(f"학생 {name} 없음")
            continue
        for key, want in wants.items():
            actual = got.get(key)
            if isinstance(want, (int, float)):
                if abs(float(actual) - want) > TOLERANCE:
                    problems.append(f"{name}.{key} 기대 {want} / 실제 {actual}")
            elif actual != want:
                problems.append(f"{name}.{key} 기대 {want} / 실제 {actual}")

    return problems


def run_case(case: dict, impl_output: dict | None = None) -> tuple[list[str], list[str], bool]:
    """(기대조건 문제, 엑셀 대조 문제, 엑셀 대조를 실제로 했는가)."""
    roster = json.loads((BASE_DIR / case["roster"]).read_text(encoding="utf-8"))
    result = impl_output if impl_output is not None else analyze(roster)

    problems = check_expectations(case, result)

    # ── 엑셀 독립 대조 ──
    excel_problems: list[str] = []
    totals = excel_totals(BASE_DIR / case["xlsx"])
    excel_checked = totals is not None
    if excel_checked:
        for key, want in totals.items():
            got = result["summary"][key]
            if abs(float(got) - float(want)) > TOLERANCE:
                excel_problems.append(f"summary.{key}  엑셀 {want} / 분석 {got}")

    # 구현 출력을 검증할 때는 레퍼런스와도 대조한다
    if impl_output is not None:
        problems += compare(analyze(roster), impl_output)

    return problems, excel_problems, excel_checked


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--impl", help="검증할 구현의 출력 JSON")
    ap.add_argument("--case", help="--impl 과 함께: 어느 케이스의 출력인지 (예: G1)")
    args = ap.parse_args()

    cases = load_cases()
    if args.impl:
        if not args.case:
            ap.error("--impl 을 쓸 때는 --case 도 필요합니다")
        cases = [c for c in cases if c["id"] == args.case]
        if not cases:
            ap.error(f"{args.case} 케이스가 없습니다")

    impl = json.loads(Path(args.impl).read_text(encoding="utf-8")) if args.impl else None

    failed, skipped = 0, 0
    for case in cases:
        problems, excel_problems, excel_checked = run_case(case, impl)

        print(f"\n── {case['id']}  {case['name']} ──")
        print(f"   {case['why']}")
        print(f"   기대 조건   {'통과' if not problems else f'실패 {len(problems)}건'}")
        for p in problems:
            print(f"     · {p}")

        if not excel_checked:
            print("   엑셀 대조   ⚠ 수행하지 못함 (openpyxl 미설치)")
            skipped += 1
        elif excel_problems:
            print(f"   엑셀 대조   실패 {len(excel_problems)}건")
            for p in excel_problems:
                print(f"     · {p}")
        else:
            print("   엑셀 대조   통과")

        if problems or excel_problems:
            failed += 1

    total = len(cases)
    print(f"\n{'=' * 58}")
    print(f"기대 조건 · 엑셀 대조   {total - failed}/{total} 케이스 통과")

    if skipped:
        # ★ 대조를 못 한 것을 통과로 세면 안 된다. 검증 기록의 근거가 사라진다.
        print(f"\n⚠ 엑셀 대조를 {skipped}/{total} 케이스에서 **수행하지 못했다.**")
        print("  AI-08-5 수치 정합성은 아직 확인되지 않은 상태다.")
        print("  python -m pip install openpyxl   후 다시 실행할 것.")
    if failed:
        print("\n실패한 케이스는 구현이 틀린 것이다. 기대 조건을 고쳐서 맞추지 말 것.")
    if not failed and not skipped:
        print("\n분석 결과가 원본 엑셀 계산값과 일치한다 (AI-08-5 수치 정합성).")
    print("=" * 58)

    sys.exit(1 if (failed or skipped) else 0)


if __name__ == "__main__":
    main()
