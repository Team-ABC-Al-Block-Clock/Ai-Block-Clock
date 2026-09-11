"""랭킹 — Semantic + Trust (가이드 §12.6).

"설명이 비슷한 에이전트"만 추천하면 말은 맞는데 실행이 자주 실패하는 것도
위로 올라온다. 우리에게는 남들에게 없는 정보가 있다 — 등급과 실행 성공률.

    FinalScore = 0.7 × Semantic + 0.3 × Trust

SchemaFit(사용자 입력 형태나 이미 고른 에이전트의 출력과 이어지는가)은 v2다.
"""

from app.config import W_SEMANTIC, W_TRUST
from app.schemas import AgentRecord


def trust_score(agent: AgentRecord) -> float:
    """등급과 성공률을 0~1 로 합친다.

    tier 1(Platinum) → 1.0, 4(Bronze) → 0.0 으로 선형 정규화하고
    성공률과 반씩 섞는다. 값은 백엔드가 인덱싱 시 넘겨준 것을 그대로 쓴다
    (등급 배치가 돌면 백엔드가 upsert 로 갱신한다).
    """
    tier = min(max(agent.tier, 1), 4)
    tier_norm = (4 - tier) / 3.0
    success = min(max(agent.success_rate, 0.0), 1.0)
    return 0.5 * tier_norm + 0.5 * success


def final_score(semantic: float, agent: AgentRecord) -> tuple[float, float]:
    """(최종 점수, trust) 를 돌려준다. semantic 은 코사인 유사도."""
    trust = trust_score(agent)
    # 코사인 유사도는 음수가 나올 수 있어 0~1 로 자른다
    sem = min(max(semantic, 0.0), 1.0)
    return W_SEMANTIC * sem + W_TRUST * trust, trust
