"""비즈니스 카탈로그 `biz.*` — 브랜딩 전략 유즈케이스가 쓰는 형식 8종.

1단계(판매자 한 사람의 워크플로우) 5종 + 2단계(에이전트 조합) 3종.
- `market_research.competition_level` 은 조건 분기의 기준값이다(로드맵 v4.2 §1.1).
- `strategy_draft` 는 차별화 · 대중 공략 두 노드의 **공통 출력**이다. 같아야 합류된다.
- `brand_strategy` 는 종단 출력이자 템플릿 바인딩 대상이라 배열 개수를 고정한다.
  서식의 칸 수가 정해져 있어서 개수가 흔들리면 PDF 가 깨진다.
- `x-source: runtime` 이 붙은 필드는 **LLM 이 채우지 않는다.** Runtime 이 주입한다.
"""
from __future__ import annotations

from typing import Annotated, Literal

from pydantic import BaseModel, Field

RUNTIME_FILLED = {"x-source": "runtime"}


# --- 조각 -----------------------------------------------------------------

class Item(BaseModel):
    # 항목 하나짜리 목록 원소
    label: str = Field(title="항목")


class Point(BaseModel):
    # 제목 + 설명 한 쌍
    label: str = Field(title="항목")
    detail: str = Field(title="설명")


class Competitor(BaseModel):
    name: str = Field(title="경쟁사명")
    positioning: str = Field(title="포지션")
    note: str = Field(title="특징")


class Reason(BaseModel):
    axis: str = Field(title="축", description="예: 기능 중심 / 메시지 중심 / 제품 중심")
    detail: str = Field(title="설명")


class Messages(BaseModel):
    core: str = Field(title="핵심 메시지")
    sub: str = Field(title="보조 메시지")
    daily: str = Field(title="일상 메시지")


class Channel(BaseModel):
    name: str = Field(title="채널")
    reason: str = Field(title="선정 이유")


class Post(BaseModel):
    phase: str = Field(title="시기", description="예: 1주차")
    theme: str = Field(title="주제")
    format: str = Field(title="형식", description="예: 릴스 · 카드뉴스")


class Kpi(BaseModel):
    name: str = Field(title="지표")
    target: str = Field(title="목표")


class Copy(BaseModel):
    text: str = Field(title="문구")


class Hashtag(BaseModel):
    tag: str = Field(title="해시태그")


class Phase(BaseModel):
    period: str = Field(title="기간")
    actions: Annotated[list[str], Field(min_length=2, max_length=5)] = Field(title="할 일")


# --- 1단계 ----------------------------------------------------------------

class BrandBrief(BaseModel):
    """브랜드 기획 요청서 — 사용자가 실행 화면에서 채우는 첫 입력(H2A 폼)."""

    brand_name: str = Field(title="브랜드명")
    category: str = Field(title="제품 카테고리")
    target: str = Field(title="핵심 고객")
    price_tier: Literal["보급형", "중가", "프리미엄"] = Field(title="가격대")
    differentiator: str = Field(title="차별점")
    comment: str | None = Field(default=None, title="추가 요청")


class MarketResearch(BaseModel):
    """시장 조사 결과 — `competition_level` 이 분기의 기준값이다."""

    market_overview_md: str = Field(title="시장 개요", description="마크다운 허용")
    competitors: Annotated[list[Competitor], Field(min_length=2, max_length=5)] = Field(title="주요 경쟁사")
    trends: Annotated[list[Item], Field(min_length=2, max_length=5)] = Field(title="최근 트렌드")
    competition_level: Literal["high", "low"] = Field(
        title="경쟁 강도", description="분기 기준값. high 면 차별화 전략, low 면 대중 공략 전략으로 간다"
    )
    level_reason: str = Field(title="판정 근거")


class ProductAnalysis(BaseModel):
    """제품 분석 결과."""

    strengths: Annotated[list[Point], Field(min_length=2, max_length=5)] = Field(title="강점")
    weaknesses: Annotated[list[Point], Field(min_length=2, max_length=5)] = Field(title="약점")
    opportunities: Annotated[list[Item], Field(min_length=2, max_length=5)] = Field(title="차별화 여지")


class StrategyDraft(BaseModel):
    """전략 초안 — 차별화 · 대중 공략 두 노드가 같은 형식으로 낸다(합류 조건)."""

    approach: Literal["차별화", "대중 공략"] = Field(
        title="접근 방식", description="어느 분기 경로를 거쳤는지 결과에 남긴다"
    )
    positioning: str = Field(title="포지셔닝")
    rationale_md: str = Field(title="근거", description="마크다운 허용")
    core_strategies: Annotated[list[Point], Field(min_length=3, max_length=5)] = Field(title="핵심 전략")


class BrandStrategy(BaseModel):
    """브랜드 전략 보고서 — 1단계 종단 출력이자 템플릿이 값을 받아 가는 형식.

    배열은 전부 3개 고정이다. 템플릿 서식의 칸 수와 맞춘다.
    `issued_on` · `author_org` 는 Runtime 이 채운다(LLM 이 날짜 · 기관명을 지어내지 않게).
    """

    brand_name: str = Field(title="브랜드명")
    issued_on: str = Field(
        title="발행일", description="Runtime 이 실행 시각으로 채운다", json_schema_extra=RUNTIME_FILLED
    )
    author_org: str = Field(
        title="작성 기관", description="Runtime 이 판매자 계정명으로 채운다", json_schema_extra=RUNTIME_FILLED
    )
    headline: str = Field(title="대제목", description="표지의 한 문장. 20자 내외")
    purpose: Annotated[list[Item], Field(min_length=3, max_length=3)] = Field(title="브랜드 목적")
    positioning: Annotated[list[Item], Field(min_length=3, max_length=3)] = Field(title="브랜드 포지셔닝")
    reasons: Annotated[list[Reason], Field(min_length=3, max_length=3)] = Field(title="선택의 이유")
    core_strategy: Annotated[list[Item], Field(min_length=3, max_length=3)] = Field(title="핵심 전략")
    messages: Messages = Field(title="메시지 체계")
    summary_md: str | None = Field(default=None, title="총평", description="마크다운 허용")


# --- 2단계 ----------------------------------------------------------------

class CampaignPlan(BaseModel):
    """SNS 캠페인 계획 — 2단계 판매자 B 의 에이전트 출력."""

    objective: str = Field(title="캠페인 목표")
    channels: Annotated[list[Channel], Field(min_length=1, max_length=4)] = Field(title="채널")
    posts: Annotated[list[Post], Field(min_length=3, max_length=6)] = Field(title="게시물 계획")
    kpis: Annotated[list[Kpi], Field(min_length=2, max_length=4)] = Field(title="성과 지표")


class AdCopy(BaseModel):
    """광고 카피 — 2단계 판매자 C 의 에이전트 출력."""

    tone: str = Field(title="톤")
    headlines: Annotated[list[Copy], Field(min_length=3, max_length=5)] = Field(title="헤드라인")
    body_copies: Annotated[list[Copy], Field(min_length=2, max_length=3)] = Field(title="본문 카피")
    hashtags: Annotated[list[Hashtag], Field(min_length=3, max_length=8)] = Field(title="해시태그")


class ExecutionPlan(BaseModel):
    """통합 실행안 — 캠페인 계획과 광고 카피를 병합해 받는 2단계 종단 출력."""

    summary_md: str = Field(title="총평", description="마크다운 허용")
    phases: Annotated[list[Phase], Field(min_length=2, max_length=4)] = Field(title="실행 단계")
    checklist: Annotated[list[Item], Field(min_length=3, max_length=8)] = Field(title="체크리스트")
