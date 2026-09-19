"""카탈로그 Pydantic 모델 → JSON Schema 산출물.

    uv run python -m src.catalog.export            # ai/schemas/catalog/ 에 쓴다
    uv run python -m src.catalog.export --check    # 산출물이 모델과 어긋나면 실패(CI 용)

산출물은 손으로 고치지 않는다. 고칠 곳은 `src/catalog/biz.py` · `common.py` 다.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

from . import CATALOG, CATALOG_VERSION, json_schema, list_entries

AI_ROOT = Path(__file__).resolve().parents[4]          # …/ai
OUT_DIR = AI_ROOT / "schemas" / "catalog"


def build() -> dict[str, str]:
    """파일 이름 → 내용(JSON 문자열)."""
    files = {
        f"{schema_id}.json": _dump(json_schema(schema_id)) for schema_id in CATALOG
    }
    files["index.json"] = _dump({"catalog_version": CATALOG_VERSION, "schemas": list_entries()})
    return files


def _dump(obj: object) -> str:
    return json.dumps(obj, ensure_ascii=False, indent=2, sort_keys=False) + "\n"


def main(argv: list[str]) -> int:
    check = "--check" in argv
    files = build()
    stale: list[str] = []

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for name, content in files.items():
        path = OUT_DIR / name
        current = path.read_text(encoding="utf-8") if path.exists() else None
        if current == content:
            continue
        if check:
            stale.append(name)
        else:
            path.write_text(content, encoding="utf-8")

    if check:
        if stale:
            print("산출물이 모델과 다릅니다: " + ", ".join(sorted(stale)), file=sys.stderr)
            print("uv run python -m src.catalog.export 를 돌린 뒤 커밋하세요.", file=sys.stderr)
            return 1
        print(f"산출물 최신 — {len(files)}개 파일")
        return 0

    print(f"{OUT_DIR} 에 {len(files)}개 파일을 썼습니다 (형식 {len(CATALOG)}종 + index)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
