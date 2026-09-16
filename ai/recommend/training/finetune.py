"""
임베딩 모델 파인튜닝 (AI-10-10 3단계)

무엇을 하는가.
  ko-sroberta-multitask 는 한국어 문장 유사도 일반을 배운 모델이다.
  "우리 마켓의 에이전트 설명 20종"에 맞춰 벡터 공간을 다시 기울인다.
  질의와 정답 에이전트는 더 가깝게, 헷갈리던 오답은 더 멀게.

손실 함수 — MultipleNegativesRankingLoss
  배치 안의 (질의, 정답) 쌍들을 모아 놓고, 각 질의에 대해 "내 정답"의 점수는
  올리고 "같은 배치에 있는 남의 정답"의 점수는 내린다. 오답을 따로 라벨링하지
  않아도 배치 자체가 오답 창고가 되는 방식이라 데이터가 적을 때 효율이 좋다.
  여기에 우리가 캔 하드 네거티브(negative 열)가 더해진다.

  ★ batch_sampler=NO_DUPLICATES 가 중요하다.
    에이전트가 20개뿐이라 배치 16개를 무작위로 담으면 같은 정답 에이전트가
    두 번 들어오기 쉽다. 그러면 MNRL 이 "남의 정답"으로 취급해 점수를 내리는데,
    그건 사실 정답이다. 모델에게 틀린 신호를 주는 셈이다. 이 옵션이 막아준다.

★★ GPU 번호를 반드시 지정한다 ★★
  SSAFY GPU 서버는 공용이다. 지정하지 않으면 모든 카드를 점유해 다른 교육생의
  작업을 막는다. torch 를 import 하기 **전에** 환경변수를 세팅해야 적용된다.
  (import 시점에 CUDA 컨텍스트가 잡히기 때문)

실행 (GPU 서버 터미널):
    cd ai/recommend/training
    export ABC_GPU_DEVICE=1          # ← 403팀 배정 번호
    nohup python finetune.py > results/finetune.log 2>&1 &
    tail -f results/finetune.log

  nohup 으로 띄우는 이유: Jupyter 세션은 24시간 뒤 끊기고, 브라우저를 닫아도
  학습이 같이 죽으면 곤란하다. 백그라운드로 분리해 둔다.
"""

import os

# ── 반드시 torch import 이전 ─────────────────────────────────────
DEVICE = os.environ.get("ABC_GPU_DEVICE", "").strip()
os.environ["CUDA_DEVICE_ORDER"] = "PCI_BUS_ID"
if DEVICE:
    os.environ["CUDA_VISIBLE_DEVICES"] = DEVICE
else:
    print("!! ABC_GPU_DEVICE 가 비어 있습니다.")
    print("!! 공용 GPU 서버에서는 팀에 배정된 번호를 반드시 지정해야 합니다:")
    print("!!     export ABC_GPU_DEVICE=1   (403팀 배정 번호)")
    print("!! CPU 로 진행합니다 — 데이터가 작아 수 분 내 끝나지만 GPU 가 훨씬 빠릅니다.\n")
    os.environ["CUDA_VISIBLE_DEVICES"] = ""
# ────────────────────────────────────────────────────────────────

import json
from pathlib import Path

import torch
from datasets import Dataset
from sentence_transformers import (
    SentenceTransformer,
    SentenceTransformerTrainer,
    SentenceTransformerTrainingArguments,
)
from sentence_transformers.losses import MultipleNegativesRankingLoss
from sentence_transformers.training_args import BatchSamplers

BASE_DIR = Path(__file__).resolve().parents[1]        # ai/recommend
DATA_DIR = BASE_DIR / "data"
MODEL_DIR = BASE_DIR / "models"
RESULT_DIR = Path(__file__).resolve().parent / "results"

TRIPLETS = DATA_DIR / "train_triplets.jsonl"
OUT_DIR = MODEL_DIR / "abc-agent-retriever-v1"        # config.py 가 이 경로를 자동 감지한다
CHECKPOINT_DIR = RESULT_DIR / "checkpoints"

BASE_MODEL = "jhgan/ko-sroberta-multitask"

EPOCHS = 5            # 340쌍은 작다. 너무 오래 돌리면 20개 카탈로그를 외워 일반화를 잃는다
BATCH_SIZE = 16
LEARNING_RATE = 2e-5  # 사전학습 가중치를 무너뜨리지 않는 관례적인 범위


def main() -> None:
    if not TRIPLETS.exists():
        raise SystemExit(
            f"{TRIPLETS.name} 이 없습니다. 순서대로 실행하세요:\n"
            f"  python split_holdout.py\n"
            f"  python mine_hard_negatives.py"
        )

    use_cuda = torch.cuda.is_available()
    print(f"장치: {'cuda:' + DEVICE if use_cuda and DEVICE else ('cuda' if use_cuda else 'cpu')}")
    if use_cuda:
        print(f"      {torch.cuda.get_device_name(0)}")

    rows = [json.loads(l) for l in TRIPLETS.read_text(encoding="utf-8").splitlines() if l.strip()]
    dataset = Dataset.from_list(rows)
    print(f"학습 삼중항 {len(dataset)}개 · {EPOCHS} epoch · batch {BATCH_SIZE}")

    model = SentenceTransformer(BASE_MODEL)
    loss = MultipleNegativesRankingLoss(model)

    args = SentenceTransformerTrainingArguments(
        output_dir=str(CHECKPOINT_DIR),
        num_train_epochs=EPOCHS,
        per_device_train_batch_size=BATCH_SIZE,
        learning_rate=LEARNING_RATE,
        warmup_ratio=0.1,
        fp16=use_cuda,                       # GPU 에서만. CPU 에서 켜면 에러
        batch_sampler=BatchSamplers.NO_DUPLICATES,   # ★ 위 설명 참고
        logging_steps=10,
        save_strategy="no",                  # 중간 체크포인트로 디스크를 채우지 않는다
        report_to="none",                    # wandb 로그인 프롬프트 방지 (공용 서버)
        seed=42,
    )

    trainer = SentenceTransformerTrainer(
        model=model, args=args, train_dataset=dataset, loss=loss
    )
    trainer.train()

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    model.save_pretrained(str(OUT_DIR))

    print(f"\n저장 완료 → {OUT_DIR}")
    print("다음: python evaluate_finetune.py  (before / after 비교)")
    print("\n※ 가중치는 커밋하지 않는다(ai/.gitignore 의 models/).")
    print("   GPU 서버에서 이 폴더를 내려받아 로컬 ai/recommend/models/ 에 두면")
    print("   app/config.py 가 자동으로 감지해 model_version 이 바뀐다.")
    print("   ★ 모델이 바뀌면 벡터 공간이 달라지므로 백엔드에 rebuild 를 요청해야 한다.")


if __name__ == "__main__":
    main()
