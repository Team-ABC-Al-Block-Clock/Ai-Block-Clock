"""템플릿 보관함 — 실행 시점에 사용자가 고르는 서식 목록.

템플릿은 스키마 하나에 묶인다. biz.brand_strategy@1 용 템플릿은 그 스키마를 내는
워크플로우에서만 고를 수 있다. 채울 키가 없으면 고를 이유가 없기 때문이다.

PoC 는 파일시스템을 쓴다. 정식 버전은 백엔드 DB + 스토리지.

    poc/library/
      index.json          목록
      tpl_default.html    플랫폼 기본 (손으로 작성 · 폴백용)
      tpl_xxxxxxxx.html   변환된 것
"""
from __future__ import annotations

import json
import re
import uuid
from dataclasses import asdict, dataclass
from pathlib import Path

LIB_DIR = Path(__file__).resolve().parent / "library"
INDEX = LIB_DIR / "index.json"


@dataclass
class Template:
    template_id: str
    name: str
    schema_id: str
    owner: str            # "platform" | "u_xxxx"
    source: str           # 원본 디자인 파일명 · "hand-written"
    is_default: bool = False

    @property
    def path(self) -> Path:
        return LIB_DIR / f"{self.template_id}.html"

    def html(self) -> str:
        return self.path.read_text(encoding="utf-8")

    def fields_used(self) -> set[str]:
        """이 템플릿이 실제로 쓰는 최상위 필드."""
        html = self.html()
        used = {m.split(".")[0].split("[")[0]
                for m in re.findall(r"\{\{\s*([A-Za-z_][\w.\[\]]*)", html)}
        used |= set(re.findall(r"\{%\s*for\s+\w+\s+in\s+([A-Za-z_]\w*)", html))
        loop_vars = set(re.findall(r"\{%\s*for\s+(\w+)\s+in", html))
        return used - loop_vars - {"loop"}


def _load() -> list[dict]:
    if not INDEX.exists():
        return []
    return json.loads(INDEX.read_text(encoding="utf-8"))


def _save(rows: list[dict]) -> None:
    LIB_DIR.mkdir(exist_ok=True)
    INDEX.write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")


def list_templates(schema_id: str | None = None) -> list[Template]:
    """실행 화면이 부르는 목록. schema_id 로 거른다 = GET /v1/templates?schema_id=…"""
    rows = [Template(**r) for r in _load()]
    rows = [t for t in rows if t.path.exists()]
    if schema_id:
        rows = [t for t in rows if t.schema_id == schema_id]
    rows.sort(key=lambda t: (not t.is_default, t.owner != "platform", t.name))
    return rows


def get(template_id: str) -> Template | None:
    return next((t for t in list_templates() if t.template_id == template_id), None)


def default_for(schema_id: str) -> Template | None:
    """폴백용 플랫폼 기본. 렌더가 실패해도 사용자는 결과물을 받아야 한다."""
    rows = list_templates(schema_id)
    return next((t for t in rows if t.owner == "platform"), rows[0] if rows else None)


def register(html: str, name: str, schema_id: str, owner: str = "u_1001",
             source: str = "", is_default: bool = False,
             template_id: str | None = None) -> Template:
    """보관함에 넣는다 = POST /v1/templates 의 마지막 단계."""
    LIB_DIR.mkdir(exist_ok=True)
    tpl = Template(template_id or f"tpl_{uuid.uuid4().hex[:8]}",
                   name, schema_id, owner, source, is_default)
    tpl.path.write_text(html, encoding="utf-8")
    rows = [r for r in _load() if r["template_id"] != tpl.template_id]
    rows.append(asdict(tpl))
    _save(rows)
    return tpl


def print_table(schema_id: str | None = None) -> list[Template]:
    rows = list_templates(schema_id)
    if not rows:
        print("보관함이 비어 있습니다.")
        return rows
    print(f"{'번호':<4}{'template_id':<16}{'이름':<22}{'소유':<10}{'출처'}")
    print("-" * 78)
    for i, t in enumerate(rows, 1):
        mark = " *" if t.is_default else "  "
        owner = "플랫폼" if t.owner == "platform" else t.owner
        print(f"{i:<4}{t.template_id:<16}{t.name[:20]:<22}{owner:<10}{t.source[:24]}{mark}")
    print("\n* = 기본값 · 렌더 실패 시 폴백")
    return rows
