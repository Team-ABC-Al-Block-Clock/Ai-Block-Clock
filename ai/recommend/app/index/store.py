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

import numpy as np

from app.schemas import AgentRecord


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
        return len(agents)

    def remove(self, agent_ids: list[str]) -> int:
        with self._lock:
            keep = [i for i, aid in enumerate(self._ids) if aid not in set(agent_ids)]
            removed = len(self._ids) - len(keep)
            if removed:
                self._ids = [self._ids[i] for i in keep]
                self._vecs = self._vecs[keep] if keep else None
                for aid in agent_ids:
                    self._meta.pop(aid, None)
        return removed

    def rebuild(self, agents: list[AgentRecord]) -> int:
        with self._lock:
            self._ids, self._meta, self._vecs = [], {}, None
        return self.upsert(agents)

    # ── 검색 ─────────────────────────────────────────────────
    def search(self, query: str, category: str | None = None) -> list[tuple[AgentRecord, float]]:
        """질의와 가까운 순으로 (에이전트, 코사인 유사도)를 돌려준다.

        하드 필터(가이드 §12.6 [2]단계)는 여기서 건다 — 비공개 에이전트는 제외.
        """
        with self._lock:
            if self._vecs is None or not self._ids:
                return []
            vecs, ids, meta = self._vecs.copy(), list(self._ids), dict(self._meta)

        q = self._encode([query])[0]
        scores = vecs @ q                       # 정규화된 벡터라 내적 = 코사인 유사도

        out = []
        for i, aid in enumerate(ids):
            agent = meta[aid]
            if not agent.is_public:
                continue
            if category and agent.category != category:
                continue
            out.append((agent, float(scores[i])))
        out.sort(key=lambda x: -x[1])
        return out

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
