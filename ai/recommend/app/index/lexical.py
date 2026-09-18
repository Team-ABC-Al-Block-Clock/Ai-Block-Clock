"""BM25 어휘 검색 (S15P21A403-280).

임베딩만으로는 왜 부족한가.
  임베딩은 "뜻이 가까운 것"을 찾는다. 단어가 겹치지 않아도 찾아내는 게 강점인데,
  뒤집으면 **단어가 정확히 겹치는 경우에 그 사실을 특별히 대우하지 않는다.**
  "점수표 넣으면 통계 내주는 거" 같은 질의에서 '점수'·'통계' 가 설명문에 그대로
  있는데도 다른 에이전트가 올라오는 일이 그래서 생긴다.

  09-17 실측: 임베딩이 틀린 holdout 질의 5개 중 BM25 는 3개를 1등으로 맞혔다.
  BM25 단독은 전체 성능이 더 낮지만(holdout 67.5% vs 82.5%), **틀리는 질의가
  서로 다르다.** 두 방식을 섞을 이유는 이것 하나뿐이다.

왜 문자 bigram 인가.
  한국어는 조사가 붙어서 공백으로 자르면 '점수표를' 과 '점수표' 가 다른 토큰이 된다.
  형태소 분석기(konlpy 등)를 쓰면 정확하지만 JVM 의존성이 붙고 컨테이너가 커진다.
  문자 2글자씩 자르면 '점수표를' → 점수·수표·표를 가 되어 '점수표' 와 두 토큰이
  겹친다. 의존성 0개로 조사 문제를 대부분 흡수한다.

BM25 가 무엇인가.
  "질의 단어가 이 문서에 몇 번 나오나"를 세되, 두 가지를 보정한다.
    ① 흔한 단어는 덜 쳐준다 (idf) — '만들기' 는 거의 모든 설명문에 있다
    ② 긴 문서가 유리하지 않게 길이로 나눈다 (b) — 설명이 길다고 더 맞는 건 아니다
  k1 은 같은 단어가 여러 번 나올 때 점수가 얼마나 더 오르는지를 정한다.
  관례값 k1=1.5 · b=0.75 를 그대로 쓴다. 에이전트 20개 규모에서 튜닝할 여지가 없다.
"""

from __future__ import annotations

import math
import re
from collections import Counter

K1 = 1.5
B = 0.75

_NON_WORD = re.compile(r"[^0-9A-Za-z가-힣]+")


def tokenize(text: str) -> list[str]:
    """문자 bigram. 한 글자 단어는 그대로 둔다."""
    out: list[str] = []
    for word in _NON_WORD.sub(" ", text).split():
        if len(word) == 1:
            out.append(word)
        else:
            out.extend(word[i:i + 2] for i in range(len(word) - 1))
    return out


class BM25Index:
    """에이전트 설명문에 대한 BM25 인덱스.

    벡터 인덱스와 같은 순서(`ids`)를 유지한다. 순서가 어긋나면 두 랭킹을
    합칠 때 엉뚱한 에이전트끼리 짝지어진다.
    """

    def __init__(self) -> None:
        self._docs: list[list[str]] = []
        self._tfs: list[Counter] = []
        self._idf: dict[str, float] = {}
        self._avgdl: float = 0.0

    def build(self, texts: list[str]) -> None:
        self._docs = [tokenize(t) for t in texts]
        self._tfs = [Counter(d) for d in self._docs]

        n = len(self._docs)
        self._avgdl = (sum(len(d) for d in self._docs) / n) if n else 0.0

        df: Counter = Counter()
        for doc in self._docs:
            df.update(set(doc))
        # 평활화된 역문서빈도. 한 문서에만 있는 토큰이 가장 크다
        self._idf = {t: math.log(1 + (n - c + 0.5) / (c + 0.5)) for t, c in df.items()}

    def scores(self, query: str) -> list[float]:
        """문서별 BM25 점수. 인덱스 순서는 build() 에 넣은 순서와 같다."""
        if not self._docs:
            return []
        q_tokens = tokenize(query)
        out = []
        for i, tf in enumerate(self._tfs):
            dl = len(self._docs[i])
            s = 0.0
            for t in q_tokens:
                f = tf.get(t)
                if not f:
                    continue
                s += self._idf[t] * f * (K1 + 1) / (f + K1 * (1 - B + B * dl / self._avgdl))
            out.append(s)
        return out


def rrf_fuse(rank_lists: list[list[int]], k: int = 60) -> dict[int, float]:
    """Reciprocal Rank Fusion — 여러 랭킹을 순위만 보고 합친다.

    왜 점수를 직접 더하지 않는가.
      코사인 유사도는 0~1 이고 BM25 는 상한이 없다. 그대로 더하면 BM25 가
      전부 먹는다. 정규화를 하려 해도 질의마다 분포가 달라 기준을 정할 수 없다.
      RRF 는 **점수를 버리고 순위만 쓴다.** 스케일 문제가 아예 사라진다.

    각 랭킹에서 r 위인 문서에 1/(k+r) 을 준다. k 가 클수록 상위권의 특혜가 줄어
    "한 랭킹에서만 1등인 문서"보다 "양쪽에서 고루 상위인 문서"가 유리해진다.
    k=60 은 원 논문(Cormack et al. 2009)의 값이고 관례로 굳었다.

    rank_lists: 각 원소는 "좋은 순으로 정렬된 문서 인덱스 목록".
    """
    fused: dict[int, float] = {}
    for ranking in rank_lists:
        for rank, doc in enumerate(ranking, start=1):
            fused[doc] = fused.get(doc, 0.0) + 1.0 / (k + rank)
    return fused
