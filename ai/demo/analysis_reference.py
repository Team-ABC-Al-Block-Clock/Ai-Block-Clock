"""
성적 분석 노드 — 결정적 계산 레퍼런스 (AI-06-3 / AI-08-5)

두 가지 역할을 한다.

  ① 분석 노드를 LLM 없이 구현할 경우의 **참조 구현**
     edu.analysis@1 의 모든 필드가 산술로 결정되므로 이것으로 충분하다.

  ② LLM으로 갈 경우의 **수치 정합성 가드 기준값** (AI-08-5)
     LLM 출력과 이 함수의 결과를 대조해 어긋나면 잡아낸다.
     어느 쪽을 택하든 이 파일은 필요하다.

★ 계산 규칙은 `ai/prompts/analysis_node.md` 의 System 프롬프트와 **한 글자도 다르면 안 된다.**
  두 곳에 규칙이 흩어지면 "프롬프트는 이렇게 시켰는데 가드는 저렇게 잰" 상황이 된다.

실행:
    cd ai/demo
    python analysis_reference.py                    # 두 데모 파일 집계
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
