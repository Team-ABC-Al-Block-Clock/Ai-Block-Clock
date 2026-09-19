"""공용 카탈로그 `common.*` — 분야를 가리지 않고 쓰는 형식 3종.

카테고리가 다른 노드끼리도 이 형식을 거치면 이어진다(플로우 문서 §1.5).
필드의 `title` 이 폼 라벨 · 엑셀 열 이름 · 템플릿 바인딩 이름이 된다.
"""
from __future__ import annotations

from typing import Annotated, Literal

from pydantic import BaseModel, Field


class Section(BaseModel):
    # 문서의 한 절. 조각 모델의 docstring 은 쓰지 않는다 — LLM 응답 스키마에 잡음으로 들어간다
    heading: str = Field(title="소제목")
    body_md: str = Field(title="본문", description="마크다운 허용")


class Column(BaseModel):
    # 표의 열 정의. label 이 엑셀 열 이름이 된다
    key: str = Field(title="열 키", description="영문 snake_case")
    label: str = Field(title="열 이름")
    type: Literal["string", "number", "boolean"] = Field(title="값 종류")


class Text(BaseModel):
    """단순 텍스트 — 형식을 따지지 않는 자유 서술."""

    title: str | None = Field(default=None, title="제목")
    body_md: str = Field(title="본문", description="마크다운 허용")


class Document(BaseModel):
    """문서 — 제목과 절로 이루어진 글."""

    title: str = Field(title="제목")
    sections: Annotated[list[Section], Field(min_length=1, max_length=10)] = Field(title="섹션")
    summary_md: str | None = Field(default=None, title="요약", description="마크다운 허용")


class Table(BaseModel):
    """표 — 엑셀 업로드(H2A)가 도착하는 자리이기도 하다."""

    title: str | None = Field(default=None, title="표 제목")
    columns: Annotated[list[Column], Field(min_length=1, max_length=30)] = Field(title="열")
    rows: Annotated[list[dict[str, str | float | bool | None]], Field(max_length=1000)] = Field(
        title="행", description="열 키를 키로 갖는 객체의 목록"
    )
