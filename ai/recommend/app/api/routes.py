"""추천 API 라우터 — 백엔드가 호출한다 (가이드 §8.2 F8 · F9)."""

import time

from fastapi import APIRouter, Header, HTTPException, Request

from app.config import SERVICE_TOKEN, TOP_K_DEFAULT, TOP_K_MAX
from app.ranking.score import final_score
from app.schemas import (
    HealthResponse,
    IndexRequest,
    IndexResponse,
    Recommendation,
    RecommendRequest,
    RecommendResponse,
    ScoreBreakdown,
)

router = APIRouter()


def _check_token(token: str | None) -> None:
    """백엔드 → Runtime 인증 (가이드 §8.1 N-9).

    내부 호출이라도 인증이 없으면 누구나 인덱스를 조작할 수 있다.
    SERVICE_TOKEN 이 비어 있으면 검사하지 않는다(로컬 개발용).
    """
    if SERVICE_TOKEN and token != SERVICE_TOKEN:
        raise HTTPException(status_code=401, detail="invalid service token")


@router.get("/health", response_model=HealthResponse)
def health(request: Request):
    index = getattr(request.app.state, "index", None)
    return HealthResponse(
        status="ok" if index is not None else "loading",
        model_version=request.app.state.model_version,
        indexed_agents=len(index) if index is not None else 0,
    )


@router.post("/v1/index/agents", response_model=IndexResponse)
def index_agents(
    body: IndexRequest,
    request: Request,
    x_abc_service_token: str | None = Header(default=None),
):
    """에이전트 인덱싱. 백엔드가 공개 · 수정 · 비공개 · 기동 시 호출한다 (가이드 §12.8)."""
    _check_token(x_abc_service_token)
    index = request.app.state.index

    if body.op == "upsert":
        n = index.upsert(body.agents)
    elif body.op == "rebuild":
        n = index.rebuild(body.agents)
    elif body.op == "remove":
        n = index.remove(body.agent_ids or [a.agent_id for a in body.agents])
    else:  # pragma: no cover — Literal 로 막혀 있다
        raise HTTPException(status_code=400, detail=f"unknown op: {body.op}")

    index.save_snapshot(request.app.state.snapshot_path)
    return IndexResponse(
        op=body.op, indexed=n, total=len(index),
        model_version=request.app.state.model_version,
    )


@router.post("/v1/recommend", response_model=RecommendResponse)
def recommend(
    body: RecommendRequest,
    request: Request,
    x_abc_service_token: str | None = Header(default=None),
):
    """자연어 질의로 마켓의 에이전트를 추천한다 (WRK-017).

    응답 목표는 1초 이내. 임베딩 1회 + 행렬곱이면 CPU 에서 수십 ms 로 끝난다.
    MVP 는 LLM 을 부르지 않는다 — 부르면 목표 시간 안에 못 들어오고 매 검색마다 비용이 든다.
    """
    _check_token(x_abc_service_token)
    t0 = time.perf_counter()

    index = request.app.state.index
    category = body.filters.category if body.filters else None
    hits = index.search(body.query, category=category)

    # 이미 캔버스에 올린 에이전트는 다시 추천하지 않는다
    exclude = set(body.selected_agent_ids)

    ranked = []
    for hit in hits:
        if hit.agent.agent_id in exclude:
            continue
        # ★ 정렬 기준은 relevance 다. 하이브리드가 꺼져 있으면 semantic 과 같고,
        #   켜져 있으면 임베딩·BM25 를 합친 값이다. breakdown 의 semantic 은
        #   진단용으로 항상 순수 코사인 유사도를 남긴다.
        score, trust = final_score(hit.relevance, hit.agent)
        ranked.append((score, trust, hit))
    ranked.sort(key=lambda x: -x[0])

    top_k = min(body.top_k or TOP_K_DEFAULT, TOP_K_MAX)
    recommendations = [
        Recommendation(
            agent_id=h.agent.agent_id, name=h.agent.name, owner_id=h.agent.owner_id,
            score=round(score, 4),
            breakdown=ScoreBreakdown(
                semantic=round(h.semantic, 4),
                trust=round(tr, 4),
                lexical=round(h.lexical, 4) if h.lexical is not None else None,
            ),
            tier=h.agent.tier, success_rate=h.agent.success_rate, category=h.agent.category,
            input_schema_id=h.agent.input_schema_id, output_schema_id=h.agent.output_schema_id,
            reason=h.agent.description,
        )
        for score, tr, h in ranked[:top_k]
    ]

    return RecommendResponse(
        recommendations=recommendations,
        model_version=request.app.state.model_version,
        latency_ms=int((time.perf_counter() - t0) * 1000),
    )
