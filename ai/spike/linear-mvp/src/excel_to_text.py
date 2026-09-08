"""엑셀 · CSV → 마크다운 표 텍스트. LLM 프롬프트에 붙이기 위한 변환.

LLM 호출 없음. 첫 시트만 읽고, 행 수 상한을 두어 토큰을 방어한다.
"""
from __future__ import annotations

from pathlib import Path

import pandas as pd

MAX_ROWS = 100


def load_table(path: str | Path) -> pd.DataFrame:
    p = Path(path)
    if p.suffix.lower() == ".csv":
        return pd.read_csv(p)
    return pd.read_excel(p, sheet_name=0)


def summarize(df: pd.DataFrame) -> str:
    numeric = df.select_dtypes("number")
    parts = [f"행 {len(df)}개, 열 {len(df.columns)}개: {', '.join(map(str, df.columns))}"]
    if not numeric.empty:
        means = ", ".join(f"{c} 평균 {numeric[c].mean():.1f}" for c in numeric.columns)
        parts.append(means)
    return "\n".join(f"- {p}" for p in parts)


def excel_to_markdown(path: str | Path, max_rows: int = MAX_ROWS) -> str:
    df = load_table(path)
    total = len(df)
    shown = df.head(max_rows).fillna("")
    table = shown.to_markdown(index=False)
    note = f"\n\n(총 {total}행 중 앞 {max_rows}행만 표시)" if total > max_rows else ""
    return f"### 표 요약\n{summarize(df)}\n\n### 표\n{table}{note}"


if __name__ == "__main__":
    import sys

    sys.stdout.reconfigure(encoding="utf-8")
    target = sys.argv[1] if len(sys.argv) > 1 else "data/demo_scores.xlsx"
    print(excel_to_markdown(target))
