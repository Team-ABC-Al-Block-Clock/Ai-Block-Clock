"""
ABC 추천 엔진 — 임베딩 모델 baseline 비교 스크립트 (AI-10-1 / S15P21A403-199)

목적: 파인튜닝 전, '사전학습 가중치 그대로'의 검색 성능을 후보 모델별로 측정한다.
      단어가 겹치지 않는 질의로도 정답 에이전트가 상위에 오는지 확인하는 것이 핵심.

지표
  Recall@K       정답이 상위 K개 안에 있는 비율          (관대함 — 3등이어도 만점)
  1등 정답률     정답이 정확히 1등인 비율
  MRR@K          정답 순위의 역수 평균 (1등=1.0, 2등=0.5, 3등=0.33)
  1·2등 격차     1등과 2등 점수 차이. 클수록 변별력이 좋다
  질의 지연      질의 1건 임베딩 + 검색에 걸린 시간

실행:  python baseline_search.py
"""

import time

import numpy as np
from sentence_transformers import SentenceTransformer

# ─────────────────────────────────────────────────────────────
# 1. 모델 선택 — 주석을 옮겨가며 후보 3개를 비교한다
# ─────────────────────────────────────────────────────────────
# MODEL_NAME = "jhgan/ko-sroberta-multitask"
# MODEL_NAME = "intfloat/multilingual-e5-base"
MODEL_NAME = "nlpai-lab/KURE-v1"

# e5 계열은 학습 시 "query: " / "passage: " 접두어를 붙여 배웠다.
# 안 붙이면 성능이 눈에 띄게 떨어지므로 모델명으로 자동 판별한다.
USE_E5_PREFIX = "e5" in MODEL_NAME

TOP_K = 3


# ─────────────────────────────────────────────────────────────
# 2. 문서 = 마켓의 에이전트 (지금은 손으로 쓴 6개짜리 장난감 카탈로그)
#    나중에 시드 에이전트 15~20개로 교체된다.
#    ★ 프롬프트 원문은 절대 넣지 않는다 (SEC-001) — 공개 메타데이터만.
# ─────────────────────────────────────────────────────────────
AGENTS = [
    {
        "id": "ag_01",
        "name": "성적 데이터 분석기",
        "description": "학생별 성적 엑셀을 받아 평균, 증감, 취약 과목을 통계 JSON으로 산출합니다.",
        "tags": ["교육", "통계", "엑셀"],
        "category": "데이터분석",
    },
    {
        "id": "ag_02",
        "name": "가정통신문 작성기",
        "description": "성적 통계를 바탕으로 학부모에게 보낼 안내문 초안을 학생별로 작성합니다.",
        "tags": ["교육", "문서작성"],
        "category": "문서정리",
    },
    {
        "id": "ag_03",
        "name": "비즈니스 이메일 드래프터",
        "description": "요청 내용을 정중한 업무용 메일 초안으로 바꿔줍니다.",
        "tags": ["메일", "업무"],
        "category": "메일초안",
    },
    {
        "id": "ag_04",
        "name": "회의록 요약기",
        "description": "긴 회의 기록을 핵심 논의와 결정사항 중심으로 요약합니다.",
        "tags": ["회의", "요약"],
        "category": "문서정리",
    },
    {
        "id": "ag_05",
        "name": "코드 리뷰 코멘트 생성기",
        "description": "변경된 코드에 대해 개선점과 리뷰 코멘트를 생성합니다.",
        "tags": ["개발", "리뷰"],
        "category": "코드리뷰",
    },
    {
        "id": "ag_06",
        "name": "출결 리포트 작성기",
        "description": "출석 데이터를 정리해 결석 추이와 특이사항 리포트를 만듭니다.",
        "tags": ["교육", "출결"],
        "category": "데이터분석",
    },
]


def agent_to_text(a: dict) -> str:
    """에이전트 하나를 임베딩에 넣을 한 덩어리 문장으로 만든다. (가이드 §12.5)"""
    return f"{a['name']}\n{a['description']}\n{' '.join(a['tags'])}\n{a['category']}"


# ─────────────────────────────────────────────────────────────
# 3. 질의 = 사용자가 랜딩 검색창에 칠 법한 말
#    ★ 뒤의 3개는 에이전트 이름과 단어가 하나도 안 겹친다. 이게 이 스크립트의 핵심.
# ─────────────────────────────────────────────────────────────
QUERIES = [
    ("성적 데이터 분석하고 싶어", "ag_01"),                      # 단어 겹침 (쉬움)
    ("회의록 요약해줘", "ag_04"),                                 # 단어 겹침 (쉬움)
    ("학원 애들 점수가 얼마나 올랐는지 보고 싶은데", "ag_01"),      # 단어 안 겹침
    ("부모님께 보낼 안내문 좀 써줘", "ag_02"),                     # 단어 안 겹침
    ("우리 팀이 짠 코드 좀 봐줄 사람 없나", "ag_05"),              # 단어 안 겹침
]


def main():
    # ── 모델 로딩 ──
    print(f"[1] 모델 로딩: {MODEL_NAME}")
    t0 = time.time()
    model = SentenceTransformer(MODEL_NAME)
    load_sec = time.time() - t0

    print(f"    로딩 완료 — {load_sec:.1f}초")
    print(f"    임베딩 차원: {model.get_embedding_dimension()}")
    print(f"    최대 입력 길이: {model.max_seq_length} 토큰")

    # 에이전트 텍스트가 모델의 최대 입력 길이를 넘으면 뒤가 잘려나간다.
    # 실제 시드 에이전트는 설명이 길어 128토큰 모델에서 문제가 될 수 있다.
    token_lens = [len(model.tokenizer.encode(agent_to_text(a))) for a in AGENTS]
    print(f"    에이전트 텍스트 토큰: 최대 {max(token_lens)} / 평균 {np.mean(token_lens):.0f}")
    if max(token_lens) > model.max_seq_length:
        print(f"    ⚠️ 최대 입력 길이({model.max_seq_length})를 초과 — 텍스트가 잘립니다")
    print()

    # ── 문서 임베딩 (인덱싱에 해당) ──
    docs = [agent_to_text(a) for a in AGENTS]
    if USE_E5_PREFIX:
        docs = [f"passage: {d}" for d in docs]

    # normalize_embeddings=True → 벡터 길이를 1로 맞춤.
    # 이러면 코사인 유사도가 그냥 내적(행렬곱) 한 번으로 계산된다.
    doc_vecs = model.encode(docs, normalize_embeddings=True)
    print(f"[2] 에이전트 {len(AGENTS)}개 인덱싱 완료 — 행렬 shape: {doc_vecs.shape}\n")

    # ── 질의 검색 ──
    recall_hits = 0       # 정답이 상위 K개 안에 있었던 횟수
    top1_hits = 0         # 정답이 정확히 1등이었던 횟수
    latencies, margins, rr = [], [], []

    for query, answer_id in QUERIES:
        q = f"query: {query}" if USE_E5_PREFIX else query

        t0 = time.time()
        q_vec = model.encode(q, normalize_embeddings=True)
        scores = doc_vecs @ q_vec          # ← 코사인 유사도 전부 한 방에
        latencies.append((time.time() - t0) * 1000)

        order = np.argsort(-scores)        # 점수 높은 순 전체 정렬
        top_idx = order[:TOP_K]

        # 1등과 2등의 점수 차이 — 클수록 정답을 확실히 구분한 것
        margins.append(scores[order[0]] - scores[order[1]])

        # 정답이 전체에서 몇 등인지 (1부터). 없으면 None
        rank = next((r for r, i in enumerate(order, 1)
                     if AGENTS[i]["id"] == answer_id), None)

        in_topk = rank is not None and rank <= TOP_K
        recall_hits += in_topk
        top1_hits += (rank == 1)
        rr.append(1 / rank if in_topk else 0.0)

        if rank == 1:
            verdict = "✅ 1등"
        elif in_topk:
            verdict = f"⚠️ {rank}등 (Recall은 통과했지만 1등을 놓침)"
        else:
            verdict = f"❌ 놓침 ({rank}등)"

        print(f"질의: {query}")
        print(f"  정답: {answer_id}  →  {verdict}   [1·2등 격차 {margins[-1]:.3f}]")
        for r, i in enumerate(top_idx, 1):
            mark = " ←정답" if AGENTS[i]["id"] == answer_id else ""
            print(f"  {r}. {scores[i]:.3f}  {AGENTS[i]['name']}{mark}")
        print()

    # ── 요약 ──
    n = len(QUERIES)
    print("─" * 58)
    print(f"모델            : {MODEL_NAME}")
    print(f"Recall@{TOP_K}       : {recall_hits}/{n} = {recall_hits / n:.2f}")
    print(f"1등 정답        : {top1_hits}/{n} = {top1_hits / n:.2f}")
    print(f"MRR@{TOP_K}          : {np.mean(rr):.3f}")
    print(f"평균 1·2등 격차 : {np.mean(margins):.3f}")
    print(f"평균 질의 지연  : {np.mean(latencies):.0f} ms")
    print(f"모델 로딩       : {load_sec:.1f}초")
    print("─" * 58)


if __name__ == "__main__":
    main()
