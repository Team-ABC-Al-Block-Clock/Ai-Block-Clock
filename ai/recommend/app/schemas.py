"""요청 · 응답 스키마 (가이드 §8.9).

★ 이 파일이 프론트 · 백엔드와의 계약 원본이다.
   필드를 바꾸면 ai/docs/recommend-api.md 도 함께 고치고 다른 파트에 통지한다.
"""

from typing import Literal

from pydantic import BaseModel, Field


# ─────────────────────────────────────────────────────────────
# [F9] POST /v1/index/agents — 인덱싱
# ─────────────────────────────────────────────────────────────
class AgentRecord(BaseModel):
    """인덱싱 대상 에이전트. ★ 공개 메타데이터만 담는다.

    프롬프트 원문과 내부 노드 구성은 절대 포함하지 않는다 (SEC-001, 가이드 §12.9).
    벡터에서 원문이 어느 정도 복원될 수 있다는 연구가 있어, 임베딩 입력에
    비공개 정보가 들어가면 '어떤 경로로도 노출되지 않아야 한다'를 정면으로 어긴다.
    """

    agent_id: str
    name: str
    description: str = ""
    tags: list[str] = Field(default_factory=list)
    category: str = ""
    input_schema_id: str | None = None
    output_schema_id: str | None = None
    owner_id: str | None = None
    tier: int = 4                      # 1(Platinum) ~ 4(Bronze)
    success_rate: float = 1.0          # 0.0 ~ 1.0
    is_public: bool = True


class IndexRequest(BaseModel):
    op: Literal["upsert", "remove", "rebuild"] = "upsert"
    agents: list[AgentRecord] = Field(default_factory=list)
    agent_ids: list[str] = Field(default_factory=list)   # op="remove" 일 때


class IndexResponse(BaseModel):
    op: str
    indexed: int                       # 이번 요청으로 반영된 건수
    total: int                         # 인덱스에 남아 있는 전체 건수
    model_version: str


# ─────────────────────────────────────────────────────────────
# [F8] POST /v1/recommend — 추천
# ─────────────────────────────────────────────────────────────
class RecommendFilters(BaseModel):
    category: str | None = None


class RecommendRequest(BaseModel):
    query: str = Field(min_length=1, description="사용자가 랜딩 검색창에 입력한 자연어")
    user_id: str | None = None
    selected_agent_ids: list[str] = Field(default_factory=list)   # 이미 고른 것 (증분 추천용, v2)
    top_k: int | None = None
    filters: RecommendFilters | None = None


class ScoreBreakdown(BaseModel):
    semantic: float                    # 임베딩 코사인 유사도 (0~1)
    trust: float                       # 등급 · 성공률 (0~1)
    schema_fit: float | None = None    # v2


class Recommendation(BaseModel):
    agent_id: str
    name: str
    owner_id: str | None = None
    score: float
    breakdown: ScoreBreakdown
    tier: int
    success_rate: float
    category: str = ""
    input_schema_id: str | None = None
    output_schema_id: str | None = None
    reason: str = ""                   # 목록에 함께 보여줄 짧은 설명 (= description)


class RecommendResponse(BaseModel):
    recommendations: list[Recommendation]
    model_version: str
    latency_ms: int


# ─────────────────────────────────────────────────────────────
# GET /health
# ─────────────────────────────────────────────────────────────
class HealthResponse(BaseModel):
    status: Literal["ok", "loading"]
    model_version: str
    indexed_agents: int
