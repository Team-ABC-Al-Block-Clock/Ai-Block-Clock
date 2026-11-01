"""
골든 케이스 생성 (S15P21A403-272)

골든 케이스가 무엇인가.
  "이 입력을 넣으면 이 결과가 나와야 한다"를 파일로 못박아 둔 것이다.
  분석 노드를 고칠 때마다 이걸 돌려서 예전에 맞던 게 여전히 맞는지 본다.
  W4 월요일 회귀 실행(AI-08-1)이 이 파일들을 쓴다.

왜 시연 데이터만으로는 부족한가 — 경계
  `demo_roster_down` 은 평균이 -5.40 으로 분기 조건(`< 0`)에서 한참 떨어져 있다.
  이런 입력만 통과시키면 **조건을 `<= 0` 으로 잘못 써도 아무 일이 없다.**
  틀린 구현이 시연 당일까지 살아남는다.

  경계 케이스는 조건이 뒤집히는 바로 그 지점을 찌른다.

생성하는 것 (G1 · G2 는 기존 시연 데이터를 그대로 쓴다)

  G3  전체 평균 변화가 **정확히 0.00**
      분기 조건이 `avg_delta < 0` 이므로 **격려형**이 나와야 한다.
      `<= 0` 으로 구현했다면 여기서 경고형이 나와 깨진다.

  G4  값 경계 모음 — trend 임계값 · weak_subject 동점 · 만점/영점
      trend 는 `>= 1.0` → up, `<= -1.0` → down, 사이는 flat 이다.
      정확히 ±1.00 인 학생을 넣어 부등호의 등호 유무를 검사한다.

실행:
    cd ai/demo
    python make_golden_cases.py
"""

import json
from pathlib import Path

GOLDEN_DIR = Path(__file__).resolve().parent / "golden"
SUBJECTS = ["국어", "영어", "수학", "과학"]
PERIOD = {"current": "2026-08", "previous": "2026-07"}


def student(name: str, pairs: list[tuple[int, int]], present: int = 20, absent: int = 0) -> dict:
    """pairs = [(previous, current), ...] 과목 순서대로 4개."""
    assert len(pairs) == len(SUBJECTS)
    return {
        "name": name,
        "scores": {sub: {"current": c, "previous": p}
                   for sub, (p, c) in zip(SUBJECTS, pairs)},
        "attendance": {"present": present, "absent": absent},
    }


def roster(students: list[dict], comment: str) -> dict:
    return {
        "schema_id": "edu.roster@1",
        "period": PERIOD,
        "subjects": SUBJECTS,
        "students": students,
        "context": {"comment": comment},
    }


# ── G3 · 전체 평균 변화가 정확히 0 ────────────────────────────────
# 5명은 전 과목 +2, 5명은 전 과목 -2. 합이 정확히 상쇄된다.
def build_g3() -> dict:
    students = []
    for i in range(5):
        base = 70 + i
        students.append(student(f"가학생{i+1}", [(base, base + 2)] * 4))
    for i in range(5):
        base = 75 + i
        students.append(student(f"나학생{i+1}", [(base, base - 2)] * 4))
    return roster(students, "이번 달은 전체적으로 지난달과 비슷했습니다. 무난하게 써주세요.")


# ── G4 · 값 경계 모음 ─────────────────────────────────────────────
def build_g4() -> dict:
    students = [
        # trend 경계 — 정확히 +1.00. `>= 1.0` 이므로 "up"
        student("경계상승", [(70, 71), (70, 71), (70, 71), (70, 71)]),
        # trend 경계 — 정확히 -1.00. `<= -1.0` 이므로 "down"
        student("경계하락", [(70, 69), (70, 69), (70, 69), (70, 69)]),
        # 사이값 +0.75 — "flat"
        student("보합상", [(70, 71), (70, 71), (70, 71), (70, 70)]),
        # 사이값 -0.75 — "flat"
        student("보합하", [(70, 69), (70, 69), (70, 69), (70, 70)]),
        # 변화 없음 0.00 — "flat"
        student("무변화", [(80, 80)] * 4),
        # weak_subject 동점 — 국어·영어가 똑같이 최저. subjects 앞쪽인 "국어"
        student("동점최저", [(60, 55), (60, 55), (90, 85), (90, 85)]),
        # 만점과 영점
        student("만점자", [(95, 100), (95, 100), (95, 100), (95, 100)]),
        student("영점자", [(10, 0), (20, 10), (30, 20), (40, 30)], present=12, absent=8),
    ]
    return roster(students, "몇몇 학생이 크게 흔들렸습니다. 개별 상황을 반영해 주세요.")


CASES = [
    {
        "id": "G1",
        "name": "정상 — 전체 하락, 경고형 분기",
        "roster": "data/demo_roster_down.json",
        "xlsx": "data/demo_roster_down.xlsx",
        "why": "시연 본편 입력. 정상 경로가 끝까지 도는지 본다.",
        "expect": {
            "student_count": 20,
            "avg_delta": -5.40,
            "branch": "warn",
            "trend_counts": {"up": 4, "down": 16},
        },
    },
    {
        "id": "G2",
        "name": "정상 — 전체 상승, 격려형 분기",
        "roster": "data/demo_roster_up.json",
        "xlsx": "data/demo_roster_up.xlsx",
        "why": "반대 분기. 같은 명단·같은 과목이라 결과 차이를 분기 하나로 설명할 수 있다.",
        "expect": {
            "student_count": 20,
            "avg_delta": 4.96,
            "branch": "encourage",
            "trend_counts": {"up": 16, "down": 4},
        },
    },
    {
        "id": "G3",
        "name": "경계 — 전체 평균 변화가 정확히 0",
        "roster": "golden/G3_zero_delta.json",
        "xlsx": "golden/G3_zero_delta.xlsx",
        "why": "분기 조건은 avg_delta < 0 이다. 정확히 0이면 격려형이어야 한다. "
               "<= 0 으로 잘못 구현했다면 여기서만 깨진다.",
        "expect": {
            "student_count": 10,
            "avg_delta": 0.0,
            "branch": "encourage",
            "trend_counts": {"up": 5, "down": 5},
        },
    },
    {
        "id": "G4",
        "name": "경계 — trend 임계값 · weak_subject 동점 · 만점/영점",
        "roster": "golden/G4_edge_values.json",
        "xlsx": "golden/G4_edge_values.xlsx",
        "why": "trend 는 >= 1.0 / <= -1.0 이다. 정확히 ±1.00 인 학생으로 등호를 검사한다. "
               "최저 점수가 두 과목에서 동점일 때 subjects 앞쪽을 고르는지도 본다.",
        "expect": {
            "student_count": 8,
            "branch": "warn",
            "students": {
                "경계상승": {"avg_delta": 1.0, "trend": "up"},
                "경계하락": {"avg_delta": -1.0, "trend": "down"},
                "보합상": {"avg_delta": 0.75, "trend": "flat"},
                "보합하": {"avg_delta": -0.75, "trend": "flat"},
                "무변화": {"avg_delta": 0.0, "trend": "flat"},
                "동점최저": {"weak_subject": "국어"},
                "만점자": {"avg_current": 100.0},
                "영점자": {"avg_current": 15.0},
            },
        },
    },
]


def to_rows(students: list[dict]) -> list[dict]:
    rows = []
    for s in students:
        row = {"학생명": s["name"]}
        for sub in SUBJECTS:
            row[f"{sub}(이번달)"] = s["scores"][sub]["current"]
            row[f"{sub}(지난달)"] = s["scores"][sub]["previous"]
        row["출석"] = s["attendance"]["present"]
        row["결석"] = s["attendance"]["absent"]
        rows.append(row)
    return rows


def main() -> None:
    GOLDEN_DIR.mkdir(parents=True, exist_ok=True)
    try:
        import pandas as pd
    except ImportError:
        raise SystemExit("pandas 가 필요합니다:  python -m pip install pandas openpyxl")

    for name, build in (("G3_zero_delta", build_g3), ("G4_edge_values", build_g4)):
        data = build()
        (GOLDEN_DIR / f"{name}.json").write_text(
            json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        pd.DataFrame(to_rows(data["students"])).to_excel(
            GOLDEN_DIR / f"{name}.xlsx", index=False, sheet_name="성적")
        print(f"{name}  학생 {len(data['students'])}명")

    (GOLDEN_DIR / "cases.json").write_text(
        json.dumps({"cases": CASES}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n케이스 정의 {len(CASES)}개 → golden/cases.json")
    print("검증: python run_golden_cases.py")


if __name__ == "__main__":
    main()
