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


def excel_to_common_table(path: str | Path, max_rows: int = MAX_ROWS) -> dict:
    """엑셀 · CSV → `common.table@1` payload.

    H2A 입력의 최소 경로다(계약 §5.4 · 범위표 5). 표의 **열 이름이 곧 라벨**이라
    카탈로그의 한국어 라벨 규칙과 그대로 맞는다. 브랜딩 유즈케이스는 폼 입력을 쓰지만
    엑셀 입력은 서비스 기능이라 남긴다.

    키는 영문 snake_case 다. 한글 열 이름은 `col_1` 처럼 순번 키를 주고 원문은 라벨에 남긴다.
    """
    df = load_table(path).head(max_rows).fillna("")
    keys = _keys_for(df.columns)
    columns = [
        {"key": key, "label": str(name),
         "type": "number" if str(df[name].dtype).startswith(("int", "float")) else "string"}
        for key, name in zip(keys, df.columns)
    ]
    rows = [
        {key: _cell(row[name]) for key, name in zip(keys, df.columns)}
        for _, row in df.iterrows()
    ]
    return {"title": Path(path).stem, "columns": columns, "rows": rows}


def _keys_for(names) -> list[str]:
    """열 이름마다 유일한 영문 키. 중복되면 순번을 붙인다."""
    keys, seen = [], set()
    for index, name in enumerate(names, start=1):
        text = str(name).strip().lower().replace(" ", "_")
        slug = "".join(ch for ch in text if ch.isascii() and (ch.isalnum() or ch == "_")).strip("_")
        # 숫자로 시작하거나 알파벳이 없는 조각은 키로 쓰지 않는다(한글 열 이름이 여기 걸린다)
        key = slug if slug[:1].isalpha() else f"col_{index}"
        if key in seen:
            key = f"{key}_{index}"
        seen.add(key)
        keys.append(key)
    return keys


def _cell(value: object) -> object:
    if isinstance(value, bool) or value is None:
        return value
    if isinstance(value, (int, float)):
        return float(value)
    return str(value)


if __name__ == "__main__":
    import sys

    sys.stdout.reconfigure(encoding="utf-8")
    target = sys.argv[1] if len(sys.argv) > 1 else "data/demo_scores.xlsx"
    print(excel_to_markdown(target))
