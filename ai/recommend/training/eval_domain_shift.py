"""
브랜딩 도메인 이동 리스크 사전 측정 (S15P21A403-278 준비)

왜 09-22 관문을 금요일로 당겨서 재는가.
  파인튜닝은 "이 20개를 서로 잘 구분해라"를 학습시킨 것이다. 학습에 쓴 합성 질의
  420개도 그 20개에서 나왔다. 이것을 **분포 내(in-distribution) 학습**이라 하고,
  학습 때 본 적 없는 종류가 들어오면 성능이 보장되지 않는다.

  09-17 로드맵 v1.2 로 유즈케이스가 가정통신문 → 브랜딩 전략으로 바뀌었는데,
  시드 20개에는 마케팅·브랜딩 에이전트가 **하나도 없다**.

      교육    ag_seed_01~05     문서·업무  ag_seed_06~13
      메일    ag_seed_14~17     개발       ag_seed_18~20

  즉 09-21 에 등록할 시연 에이전트 4개는 이 모델이 한 번도 본 적 없는 도메인이다.
  로드맵은 이 확인을 09-22(화)에 두었지만, 거기서 처음 재고 안 나오면 대응할 날이
  수요일 리허설 하루뿐이다. 설명문을 고쳐 가며 다시 재려면 시간이 필요하다.

무엇을 재는가.

  ① 브랜딩 질의로 시연 에이전트 4개가 상위에 오르는가
       ★ 특히 위험한 쌍: "광고 카피 생성기" vs ag_seed_14 비즈니스 이메일 드래프터
         · ag_seed_16 사과문 초안 작성기. 셋 다 "짧은 글을 써 주는" 에이전트인데,
         모델은 14·16 은 구분하도록 학습했지만 새로 온 카피와의 관계는 모른다.

  ② 에이전트 4개를 더한 뒤에도 골든 22개 · holdout 80개가 유지되는가 (회귀)
       시연 마켓에는 24개가 같이 올라간다. 새 에이전트가 기존 정답을 밀어낼 수 있다.

       ★ holdout 80개를 반드시 같이 잰다 (09-18 1차 측정에서 빠뜨렸던 부분).
         골든 22개는 사람이 쓴 쉬운 질의라 파인튜닝 전에도 이미 100% 였다.
         파인튜닝의 실제 이득은 holdout 에서 났다 (70.0% → 82.5%, 09-14).
         holdout 을 빼고 비교하면 **파인튜닝의 장점이 측정 항목에서 통째로 빠져서**
         "사전학습이 더 낫다"는 잘못된 결론이 나온다.

  ③ 설정 3종 중 어느 것이 브랜딩에 유리한가
       파인튜닝+weighted / 파인튜닝+off / 사전학습+weighted

왜 사전학습(파인튜닝 전)을 같이 재는가.
  app/config.py 가 이렇게 돼 있다.

      if FINETUNED_DIR.exists():  MODEL_PATH = 파인튜닝
      else:                       MODEL_PATH = 사전학습

  **models/ 폴더만 치우면 코드 수정 0줄로 사전학습 모델로 되돌아간다.**
  파인튜닝이 브랜딩에서 오히려 불리하다면 이게 그대로 탈출구다. 시연 전날에
  "어쩌지"가 아니라 "환경 하나 빼면 된다"가 되도록 미리 확인해 둔다.

실행:
    cd ai/recommend
    python training/eval_domain_shift.py

    # 사전학습 모델 비교를 건너뛰려면 (인터넷이 막혔을 때)
    python training/eval_domain_shift.py --skip-pretrained
"""

import argparse
import json
import sys
from pathlib import Path

BASE = Path(__file__).resolve().parents[1]          # ai/recommend
sys.path.insert(0, str(BASE))
sys.path.insert(0, str(BASE / "training"))

from app.config import FINETUNED_DIR, PRETRAINED, W_LEXICAL   # noqa: E402
from app.index.lexical import BM25Index                        # noqa: E402
from catalog import build_agent_text, load_agents, load_queries, load_schemas  # noqa: E402
from eval_hybrid import fuse                                   # noqa: E402

# ★ 융합 계산을 eval_hybrid.py 에서 가져다 쓴다. 직접 복사하면 같은 수식이
#   세 곳(store.py · eval_hybrid.py · 여기)에 생기고, 한 곳만 고치면
#   "평가에서는 좋았는데 서비스에서는 다른" 상태가 된다.

BRAND_DRAFT = BASE / "data" / "brand_agents_draft.json"
HOLDOUT = BASE / "data" / "holdout_pairs.jsonl"
RESULT = Path(__file__).resolve().parent / "results" / "eval_domain_shift.json"

INCLUDE_SCHEMA = False      # 서비스(store.py)와 같은 규칙 (09-09 결정)
TOP_K = 8                   # 화면에 뜨는 개수 (config.TOP_K_DEFAULT)

# 시연 질의 후보. 학원 원장이 아니라 **브랜드 담당자**가 칠 법한 말투로 쓴다.
# ★ 에이전트 이름을 그대로 치면 당연히 잘 나온다. 그건 시연 가치가 없으므로
#   "브랜딩" · "카피" 같은 이름 단어를 피한 문장을 절반 섞어 둔다.
QUERIES = [
    # (질의, 이 질의로 찾아야 하는 agent_id)
    ("신제품 내놓는데 어떤 컨셉으로 갈지 방향 좀 잡아줘",          "ag_brand_01"),
    ("우리 제품 시장에서 어떻게 자리잡을지 전략 세워줘",            "ag_brand_01"),
    ("브랜드 전략 만들어줘",                                      "ag_brand_01"),
    ("인스타랑 유튜브에 뭘 언제 올릴지 계획 짜줘",                 "ag_brand_02"),
    ("소셜미디어 캠페인 기획해줘",                                "ag_brand_02"),
    ("광고에 쓸 문구랑 해시태그 뽑아줘",                           "ag_brand_03"),
    ("한 줄로 각인되는 홍보 문장 만들어줘",                        "ag_brand_03"),
    ("앞에서 나온 캠페인이랑 문구를 일정이랑 예산으로 묶어줘",       "ag_brand_04"),
    ("채널별로 언제 뭘 집행할지 예산까지 정리해줘",                "ag_brand_04"),
]

CONFIGS = [
    # (표시 이름, 파인튜닝 사용 여부, 융합 모드)
    ("파인튜닝 + weighted", True,  "weighted"),
    ("파인튜닝 + off",      True,  "off"),
    ("사전학습 + weighted", False, "weighted"),
]


def rank_of(order_ids: list[str], answers: set[str]) -> int | None:
    for i, a in enumerate(order_ids, 1):
        if a in answers:
            return i
    return None


def make_ranker(model, ids, texts, mode):
    """질의 목록을 한 번에 인코딩해 순위를 낸다.

    ★ 질의마다 encode 를 부르면 111개 × 3설정 = 333번이 된다. 배치로 넘기면
      한 번에 처리돼 훨씬 빠르다. 결과는 같다.
    """
    agent_vecs = model.encode(texts, normalize_embeddings=True, show_progress_bar=False)
    bm25 = BM25Index()
    bm25.build(texts)

    def rank(queries: list[str]) -> list[tuple[list[str], float]]:
        q_vecs = model.encode(queries, normalize_embeddings=True, show_progress_bar=False)
        out = []
        for q, qv in zip(queries, q_vecs):
            semantic = (agent_vecs @ qv).tolist()
            lexical = bm25.scores(q) if mode != "off" else []
            rel = fuse(mode, semantic, lexical, W_LEXICAL)
            order = sorted(range(len(ids)), key=lambda i: -rel[i])
            margin = rel[order[0]] - rel[order[1]]
            out.append(([ids[i] for i in order], margin))
        return out

    return rank


def score_set(rank, pairs) -> dict:
    """pairs: (질의, 정답 agent_id 집합) 목록. 1등 정답률 · 격차 · 실패 목록."""
    ranked = rank([q for q, _ in pairs])
    hits, margins, misses = 0, [], []
    for (q, answers), (order, margin) in zip(pairs, ranked):
        if order[0] in answers:
            hits += 1
            margins.append(margin)          # 맞혔을 때만 잰다 — 틀렸을 때의 격차는 클수록 나쁘다
        else:
            misses.append({"query": q, "got": order[0],
                           "want": sorted(answers), "rank": rank_of(order, answers)})
    n = len(pairs)
    return {"n": n, "top1": hits, "rate": hits / n if n else 0.0,
            "margin": float(sum(margins) / len(margins)) if margins else 0.0,
            "misses": misses}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--skip-pretrained", action="store_true",
                    help="사전학습 모델 비교를 건너뛴다 (내려받기가 막혔을 때)")
    args = ap.parse_args()

    for f in (BRAND_DRAFT, HOLDOUT):
        if not f.exists():
            raise SystemExit(f"{f.name} 이 없습니다.")
    if not FINETUNED_DIR.exists():
        raise SystemExit(f"파인튜닝 가중치가 없습니다: {FINETUNED_DIR}")

    from sentence_transformers import SentenceTransformer

    schemas = load_schemas()
    seed = load_agents()
    brand = json.loads(BRAND_DRAFT.read_text(encoding="utf-8"))["agents"]
    agents = seed + brand

    ids = [a["agent_id"] for a in agents]
    texts = [build_agent_text(a, schemas, include_schema=INCLUDE_SCHEMA) for a in agents]
    names = {a["agent_id"]: a["name"] for a in agents}

    # 세 평가 세트. 성격이 달라서 셋을 같이 봐야 판단이 선다.
    #   브랜딩  새 도메인이 걸리는가          (이번에 추가된 것)
    #   골든    사람이 쓴 질의 — 회귀 방지    (파인튜닝 전에도 100% 였던 쉬운 세트)
    #   holdout 학습에 안 쓴 합성 질의 80개  (파인튜닝의 실제 이득이 여기서 났다)
    brand_pairs = [(q, {want}) for q, want in QUERIES]
    golden = [(q["query"], set(q["answers"])) for q in load_queries()]
    holdout = [(json.loads(l)["query"], {json.loads(l)["positive_agent_id"]})
               for l in HOLDOUT.read_text(encoding="utf-8").splitlines() if l.strip()]
    SETS = [("브랜딩", brand_pairs), ("골든", golden), ("holdout", holdout)]

    print(f"색인 대상 {len(agents)}개  (시드 {len(seed)} + 브랜딩 {len(brand)})")
    print("평가 세트  " + " · ".join(f"{k} {len(v)}개" for k, v in SETS) + "\n")

    results = {}
    for label, use_ft, mode in CONFIGS:
        if not use_ft and args.skip_pretrained:
            print(f"[건너뜀] {label}")
            continue
        path = str(FINETUNED_DIR) if use_ft else PRETRAINED
        print(f"모델 로딩: {path}")
        try:
            model = SentenceTransformer(path)
        except Exception as e:
            print(f"  실패 — {type(e).__name__}: {e}")
            print("  (사전학습 모델은 내려받기가 필요하다. --skip-pretrained 로 건너뛴다)\n")
            continue

        rank = make_ranker(model, ids, texts, mode)
        results[label] = {k: score_set(rank, v) for k, v in SETS}
        r = results[label]
        print("  " + " · ".join(
            f"{k} {r[k]['top1']}/{r[k]['n']}" for k, _ in SETS) + "\n")

    if not results:
        raise SystemExit("측정된 설정이 없습니다.")

    # ── 표 ──
    print("=" * 84)
    print(f"{'설정':22}" + "".join(f"{k:>16}" for k, _ in SETS) + f"{'격차(holdout)':>16}")
    print("-" * 84)
    for label, r in results.items():
        cells = "".join(f"{r[k]['top1']:>7}/{r[k]['n']:<3}{r[k]['rate']:>5.0%}" for k, _ in SETS)
        print(f"{label:22}{cells}{r['holdout']['margin']:>16.4f}")
    print("=" * 84)

    # ── 실패 상세 ──
    for label, r in results.items():
        bad = [(k, m) for k, _ in SETS for m in r[k]["misses"]]
        if not bad:
            continue
        print(f"\n── 놓친 질의 · {label} ──")
        for k, m in bad[:12]:
            got, want = names.get(m["got"], m["got"]), names.get(m["want"][0], m["want"][0])
            rk = f"{m['rank']}위" if m["rank"] else "권외"
            print(f"  [{k}] {m['query'][:34]:36} 정답 {want[:14]:16}{rk:>5}  ← 1위 {got[:16]}")

    # ── 판정 ──
    print("\n" + "=" * 84)
    base = next(iter(results))
    print("판정은 세 세트를 같이 본다. 한 세트만 보면 결론이 뒤집힌다.")
    print("  · 골든은 파인튜닝 전에도 100% 였다 — 여기서 이겼다고 더 나은 모델이 아니다")
    print("  · 파인튜닝의 이득은 holdout 에서 났다 (70.0% → 82.5%, 09-14)")
    print("  · 브랜딩은 '상위 8 안에 드는가'가 관문이다. 1등 여부는 시연 질의 선택으로 푼다")
    print("-" * 84)
    best = max(results.items(), key=lambda kv: (kv[1]["holdout"]["top1"] + kv[1]["골든"]["top1"],
                                                kv[1]["브랜딩"]["top1"]))
    print(f"세 세트 합산 최고: {best[0]}")
    print("=" * 84)

    RESULT.parent.mkdir(parents=True, exist_ok=True)
    RESULT.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n결과 저장 → results/{RESULT.name}")


if __name__ == "__main__":
    main()
