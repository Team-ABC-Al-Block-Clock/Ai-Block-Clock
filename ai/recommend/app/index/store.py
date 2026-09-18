"""인메모리 벡터 인덱스 (가이드 §7.4 · §12.7).

에이전트 20~30개 규모라 벡터 DB는 과하다. numpy 행렬 하나면 충분하고,
질의는 행렬곱 한 번으로 끝난다 (CPU 수십 ms).

★ 이 인덱스는 '상태'가 아니라 '파생 캐시'다.
  원본(source of truth)은 백엔드 MySQL 의 에이전트 메타데이터이고,
  Runtime 이 재기동되면 백엔드가 rebuild 로 전체를 다시 보낸다.
  그래서 stateless 원칙과 충돌하지 않는다.
  스냅샷 파일은 재기동을 빠르게 하기 위한 것일 뿐, 없어도 동작한다.
"""

from __future__ import annotations

import threading
from pathlib import Path
from typing import NamedTuple

import numpy as np

from app.config import HYBRID_MODE, RRF_K, W_LEXICAL
from app.index.lexical import BM25Index, rrf_fuse
from app.schemas import AgentRecord


class Hit(NamedTuple):
    """검색 결과 한 건.

    semantic  임베딩 코사인 유사도 (진단용으로 항상 남긴다)
    lexical   BM25 점수. 하이브리드가 꺼져 있으면 None
    relevance 최종 정렬에 쓰는 값. 하이브리드가 꺼져 있으면 semantic 과 같다
    """
    agent: AgentRecord
    semantic: float
    lexical: float | None
    relevance: float


def build_agent_text(agent: AgentRecord) -> str:
    """임베딩에 넣을 문서 텍스트.

    ★ 공개 메타데이터만 (SEC-001).
    09-09 측정 결과 입출력 스키마 설명은 넣지 않기로 했다 —
    카탈로그 설명문을 개선한 뒤로는 없는 편이 1·2등 격차가 더 크고(0.115 vs 0.096)
    선정 모델의 128토큰 제한에도 걸리지 않는다. 근거는 training/results/log.md.
    """
    return "\n".join([
        agent.name,
        agent.description,
        " ".join(agent.tags),
        agent.category,
    ])


class AgentIndex:
    """에이전트 벡터 인덱스. 스레드 안전하게 읽고 쓴다."""

    def __init__(self, encode_fn, model_version: str):
        self._encode = encode_fn                 # (list[str]) -> np.ndarray (정규화된 벡터)
        self.model_version = model_version
        self._lock = threading.Lock()
        self._ids: list[str] = []
        self._meta: dict[str, AgentRecord] = {}
        self._vecs: np.ndarray | None = None     # (N, dim) — 행 순서는 self._ids 와 같다
        self._bm25 = BM25Index()                 # 행 순서를 self._ids 와 맞춰 유지한다

    # ── 조회 ─────────────────────────────────────────────────
    def __len__(self) -> int:
        return len(self._ids)

    # ── 쓰기 ─────────────────────────────────────────────────
    def upsert(self, agents: list[AgentRecord]) -> int:
        if not agents:
            return 0
        vecs = self._encode([build_agent_text(a) for a in agents])

        with self._lock:
            for agent, vec in zip(agents, vecs):
                if agent.agent_id in self._meta:
                    row = self._ids.index(agent.agent_id)
                    self._vecs[row] = vec
                else:
                    self._ids.append(agent.agent_id)
                    self._vecs = vec[None, :] if self._vecs is None else np.vstack([self._vecs, vec])
                self._meta[agent.agent_id] = agent
            self._rebuild_bm25()
        return len(agents)

    def _rebuild_bm25(self) -> None:
        """BM25 는 문서빈도(idf)가 전체 집합에 의존해서 한 건만 갱신할 수 없다.

        에이전트 20~30개 규모라 통째로 다시 만드는 비용이 무시할 수준이다.
        ★ 반드시 self._ids 순서로 만든다. 순서가 어긋나면 두 랭킹을 합칠 때
          엉뚱한 에이전트끼리 짝지어진다.
        """
        self._bm25.build([build_agent_text(self._meta[aid]) for aid in self._ids])

    def remove(self, agent_ids: list[str]) -> int:
        with self._lock:
            keep = [i for i, aid in enumerate(self._ids) if aid not in set(agent_ids)]
            removed = len(self._ids) - len(keep)
            if removed:
                self._ids = [self._ids[i] for i in keep]
                self._vecs = self._vecs[keep] if keep else None
                for aid in agent_ids:
                    self._meta.pop(aid, None)
                self._rebuild_bm25()
        return removed

    def rebuild(self, agents: list[AgentRecord]) -> int:
        with self._lock:
            self._ids, self._meta, self._vecs = [], {}, None
        return self.upsert(agents)

    # ── 검색 ─────────────────────────────────────────────────
    def search(self, query: str, category: str | None = None) -> list[Hit]:
        """질의와 가까운 순으로 Hit 목록을 돌려준다.

        하드 필터(가이드 §12.6 [2]단계)는 여기서 건다 — 비공개 에이전트는 제외.
        ★ 필터를 융합 **전에** 걸면 안 된다. 순위가 후보 집합에 따라 달라지므로,
          같은 에이전트가 카테고리 필터 유무에 따라 다른 점수를 받게 된다.
          그래서 전체로 융합한 뒤 마지막에 거른다.
        """
        with self._lock:
            if self._vecs is None or not self._ids:
                return []
            vecs, ids, meta = self._vecs.copy(), list(self._ids), dict(self._meta)
            bm25 = self._bm25

        q = self._encode([query])[0]
        semantic = (vecs @ q).tolist()          # 정규화된 벡터라 내적 = 코사인 유사도

        lexical: list[float] | None = None
        relevance = semantic

        if HYBRID_MODE in ("rrf", "weighted"):
            lexical = bm25.scores(query)
            if len(lexical) != len(ids):        # 색인이 어긋난 경우 — 임베딩만 쓴다
                lexical = None
            elif HYBRID_MODE == "rrf":
                relevance = self._fuse_rrf(semantic, lexical)
            else:
                relevance = self._fuse_weighted(semantic, lexical)

        out = []
        for i, aid in enumerate(ids):
            agent = meta[aid]
            if not agent.is_public:
                continue
            if category and agent.category != category:
                continue
            out.append(Hit(
                agent=agent,
                semantic=float(semantic[i]),
                lexical=float(lexical[i]) if lexical is not None else None,
                relevance=float(relevance[i]),
            ))
        out.sort(key=lambda h: -h.relevance)
        return out

    @staticmethod
    def _fuse_rrf(semantic: list[float], lexical: list[float]) -> list[float]:
        """순위만 보고 합친다. 점수 스케일 차이를 아예 없앤다.

        이론상 최댓값 2/(k+1) 로 나눠 0~1 로 맞춘다. **질의별 최댓값이 아니라
        이론값으로 나눈다** — 질의마다 다른 값으로 나누면 1등이 항상 1.0 이 되어
        질의 간 점수를 비교할 수 없고, 1·2등 격차도 사라진다.
        """
        n = len(semantic)
        sem_rank = sorted(range(n), key=lambda i: -semantic[i])
        lex_rank = sorted(range(n), key=lambda i: -lexical[i])
        fused = rrf_fuse([sem_rank, lex_rank], k=RRF_K)
        ceiling = 2.0 / (RRF_K + 1)
        return [fused.get(i, 0.0) / ceiling for i in range(n)]

    @staticmethod
    def _fuse_weighted(semantic: list[float], lexical: list[float]) -> list[float]:
        """임베딩 점수에 어휘 점수를 얹는다.

        BM25 는 상한이 없어서 그대로 더하면 임베딩을 덮어버린다. 질의별 최댓값으로
        나눠 0~1 로 만든 뒤 작은 가중치로 섞는다. 순위를 뒤집는 힘은 약하지만
        **엇비슷한 후보들 사이에서 단어가 겹치는 쪽을 밀어 올린다** — 09-17 실측에서
        임베딩이 틀린 5개가 대부분 이런 근소한 차이였다.
        """
        top = max(lexical) if lexical else 0.0
        lex_norm = [(x / top if top > 0 else 0.0) for x in lexical]
        return [(1 - W_LEXICAL) * max(0.0, min(1.0, s)) + W_LEXICAL * l
                for s, l in zip(semantic, lex_norm)]

    # ── 스냅샷 ───────────────────────────────────────────────
    def save_snapshot(self, path: Path) -> None:
        with self._lock:
            if self._vecs is None:
                return
            payload = [m.model_dump_json() for m in self._meta.values()]
            ids = list(self._ids)
            vecs = self._vecs.copy()
        np.savez_compressed(
            path, vecs=vecs, ids=np.array(ids, dtype=object),
            meta=np.array(payload, dtype=object),
            model_version=np.array([self.model_version], dtype=object),
        )

    def load_snapshot(self, path: Path) -> bool:
        """스냅샷을 읽는다. 모델 버전이 다르면 버린다 (벡터 공간이 다르므로)."""
        if not path.exists():
            return False
        try:
            z = np.load(path, allow_pickle=True)
            if str(z["model_version"][0]) != self.model_version:
                return False
            metas = [AgentRecord.model_validate_json(s) for s in z["meta"]]
            with self._lock:
                self._vecs = z["vecs"]
                self._ids = [str(x) for x in z["ids"]]
                self._meta = {m.agent_id: m for m in metas}
            return True
        except Exception:
            return False
