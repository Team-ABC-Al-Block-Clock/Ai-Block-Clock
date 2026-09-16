"""
시드 카탈로그 · 스키마 · 골든 쿼리 로더 (AI-10-1 / AI-10-6 공통 모듈)

왜 따로 뺐는가.
  check_token_length.py 와 baseline_search.py 가 각자 텍스트를 조립하면
  "토큰 길이는 A로 쟀는데 실제 검색은 B로 한" 상황이 생긴다. 조립 규칙이
  한 곳에만 있어야 두 측정이 같은 것을 가리킨다.
  금요일 인덱싱 파이프라인(AI-10-2)도 이 build_agent_text() 를 그대로 쓴다.

데이터 파일 (ai/recommend/data/)
  seed_agents.json      시드 에이전트 20개. POST /v1/index/agents 요청과 같은 형식
  schema_catalog.json   공용 스키마 6종의 사람이 읽는 설명
  golden_queries.json   평가용 질의 20개 + 정답 라벨
"""

import json
from pathlib import Path

DATA_DIR = Path(__file__).resolve().parents[1] / "data"


def _read(name: str) -> dict:
    return json.loads((DATA_DIR / name).read_text(encoding="utf-8"))


def load_agents() -> list[dict]:
    """시드 에이전트 목록. 각 항목의 식별자는 agent_id 다."""
    return _read("seed_agents.json")["agents"]


def load_schemas() -> dict:
    """스키마 ID → {summary, fields} 설명 사전."""
    return _read("schema_catalog.json")["schemas"]


def load_queries() -> list[dict]:
    """골든 쿼리 목록. query / answers(정답 agent_id 목록) / word_overlap."""
    return _read("golden_queries.json")["queries"]


def build_agent_text(agent: dict, schemas: dict, include_schema: bool = True) -> str:
    """에이전트 하나를 임베딩에 넣을 한 덩어리 텍스트로 조립한다. (가이드 §12.5)

    이름 · 설명 · 태그 · 카테고리 (+ 입출력 스키마 설명).
    ★ 전부 공개 메타데이터다. 프롬프트 원문과 내부 노드 구성은
      어떤 경로로도 들어가면 안 된다 (SEC-001, 가이드 §12.9).

    include_schema=False 로 두면 스키마 설명을 뺀 짧은 텍스트가 된다.
    최대 입력 128토큰인 ko-sroberta 에서 잘림이 생길 때의 대안이자,
    "스키마 설명이 검색 품질에 도움이 되는가"를 재는 비교군이다.
    """
    parts = [
        agent["name"],
        agent["description"],
        " ".join(agent["tags"]),
        agent["category"],
    ]
    if include_schema:
        src = schemas.get(agent["input_schema_id"], {})
        dst = schemas.get(agent["output_schema_id"], {})
        parts.append(f"입력: {src.get('summary', '')} — {src.get('fields', '')}")
        parts.append(f"출력: {dst.get('summary', '')} — {dst.get('fields', '')}")
    return "\n".join(parts)
