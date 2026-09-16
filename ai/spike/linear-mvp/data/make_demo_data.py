"""합성 성적표 생성 — 학생 20명 · 국어/영어/수학 · 2월/3월 · 출결.

실제 학생 정보를 쓰지 않는다. 전체 평균은 3월이 약간 하락하고,
뚜렷하게 하락한 학생 3명과 상승한 학생 3명을 심는다.

실행:  uv run python data/make_demo_data.py
출력:  data/demo_scores.xlsx
"""
from __future__ import annotations

import random
from pathlib import Path

import pandas as pd

SEED = 403
NAMES = [
    "김서준", "이하윤", "박도윤", "최지우", "정시우", "강하은", "조예준", "윤서아",
    "장민준", "임지아", "한주원", "오수아", "서지호", "신유나", "권건우", "황다은",
    "안현우", "송채원", "전이준", "홍소율",
]
SUBJECTS = ["국어", "영어", "수학"]
DECLINERS = {"박도윤", "장민준", "황다은"}   # 3월에 크게 하락
RISERS = {"최지우", "오수아", "전이준"}       # 3월에 크게 상승


def clamp(x: float, lo: int = 30, hi: int = 100) -> int:
    return int(max(lo, min(hi, round(x))))


def make_rows() -> list[dict]:
    rng = random.Random(SEED)
    rows = []
    for name in NAMES:
        base = rng.gauss(74, 11)
        row = {"이름": name}
        for subj in SUBJECTS:
            feb = clamp(base + rng.gauss(0, 7))
            if name in DECLINERS:
                delta = rng.uniform(-18, -10)
            elif name in RISERS:
                delta = rng.uniform(10, 18)
            else:
                delta = rng.gauss(-1.5, 5)          # 전체적으로 소폭 하락
            row[f"{subj}_2월"] = feb
            row[f"{subj}_3월"] = clamp(feb + delta)
        absent = rng.choice([0, 0, 0, 1, 1, 2, 3])
        row["출석"] = 20 - absent
        row["결석"] = absent
        rows.append(row)
    return rows


def main() -> None:
    out = Path(__file__).with_name("demo_scores.xlsx")
    df = pd.DataFrame(make_rows())
    df.to_excel(out, index=False, sheet_name="3월 성적")
    feb = df[[f"{s}_2월" for s in SUBJECTS]].to_numpy().mean()
    mar = df[[f"{s}_3월" for s in SUBJECTS]].to_numpy().mean()
    print(f"saved {out}  ({len(df)} students)  전체 평균 2월 {feb:.1f} → 3월 {mar:.1f}")


if __name__ == "__main__":
    main()
