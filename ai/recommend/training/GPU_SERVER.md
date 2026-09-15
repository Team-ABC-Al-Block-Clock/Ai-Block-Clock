# GPU 서버에서 파인튜닝 돌리기 (AI-10-10)

SSAFY GPU 개발 서버는 **공용**이다. 아래 규칙은 편의가 아니라 다른 교육생의
작업을 죽이지 않기 위한 제약이다.

| 제약 | 이유 |
| --- | --- |
| GPU 번호를 반드시 지정한다 | 지정하지 않으면 모든 카드를 점유한다 |
| `sudo` 없음 | Conda 가상환경 안에 설치한다 |
| Jupyter 세션 24시간 만료 | 학습은 `nohup` 으로 분리해 띄운다 |
| 종료 후 좀비 프로세스 확인 | 메모리를 계속 잡고 있으면 남이 못 쓴다 |

---

## 0. 접속

1. VPN `https://server.ssafy.com` 접속
2. JupyterHub 로그인 (http)
3. 우상단 **New ▼ → Terminal**

---

## 1. 파일 올리기

파인튜닝에 필요한 것은 `ai/recommend` 아래 이 파일들뿐이다. (모델 가중치·venv 제외)

```
ai/recommend/
├─ data/
│   ├─ seed_agents.json
│   ├─ schema_catalog.json
│   ├─ golden_queries.json
│   └─ synthetic_queries.jsonl
└─ training/
    ├─ catalog.py
    ├─ split_holdout.py
    ├─ mine_hard_negatives.py
    ├─ finetune.py
    └─ evaluate_finetune.py
```

Jupyter 파일 목록 화면에서 **Upload** 로 올린다. 폴더 구조를 그대로 유지해야
`catalog.py` 의 상대 경로(`parents[1] / "data"`)가 맞는다.

폴더가 없으면 터미널에서 먼저 만든다.

```bash
mkdir -p ~/abc/recommend/data ~/abc/recommend/training/results
```

---

## 2. Conda 환경

```bash
conda create -n abc python=3.11 -y
conda activate abc

pip install -U "sentence-transformers>=3.4" "datasets" "accelerate"
```

`sentence-transformers` 가 torch 를 같이 끌고 온다. CUDA 버전이 맞는지 확인:

```bash
python -c "import torch; print(torch.__version__, torch.cuda.is_available(), torch.cuda.device_count())"
```

`False` 가 나오면 CPU 판 torch 가 깔린 것이다. 데이터가 작아서 CPU 로도
수 분이면 끝나니 일정상 그냥 진행해도 된다.

---

## 3. GPU 번호 — 고르는 게 아니라 배정받는다

우리 팀에 **배정된 번호는 `1`** 이다. 임의로 다른 번호를 쓰면 다른 팀의 작업과
같은 카드를 물게 된다. `nvidia-smi` 는 카드를 고르려고 보는 게 아니라
**내 카드가 제대로 잡혔는지 · 끝난 뒤 메모리를 놓았는지** 확인하는 용도다.

```bash
nvidia-smi
```

---

## 4. 실행 — 순서대로

```bash
cd ~/abc/recommend/training

# ① 학습/평가 분할 (340 / 80)
python split_holdout.py

# ② 하드 네거티브 채굴
python mine_hard_negatives.py

# ③ 파인튜닝 — 백그라운드로 띄운다
export ABC_GPU_DEVICE=1          # ← 팀 배정 번호. 임의로 바꾸지 않는다
nohup python finetune.py > results/finetune.log 2>&1 &

# 진행 보기 (Ctrl+C 로 빠져나와도 학습은 계속 돈다)
tail -f results/finetune.log

# ④ 학습이 끝나면 before / after 비교
python evaluate_finetune.py
```

`nohup ... &` 로 띄우면 브라우저를 닫거나 Jupyter 세션이 만료돼도 학습이 살아 있다.

---

## 5. 결과 내려받기

Jupyter 파일 목록에서 폴더는 한 번에 못 받으므로 압축한다.

```bash
cd ~/abc/recommend
tar czf abc-agent-retriever-v1.tar.gz models/abc-agent-retriever-v1
```

`abc-agent-retriever-v1.tar.gz` 와 `training/results/eval_finetune.json` 을 다운로드한다.

로컬에서 풀어 넣을 위치:

```
ai/recommend/models/abc-agent-retriever-v1/
```

`app/config.py` 가 이 폴더의 존재를 보고 자동으로 모델을 바꾼다. 코드 수정은 없다.

```python
if FINETUNED_DIR.exists():
    MODEL_PATH = str(FINETUNED_DIR); MODEL_VERSION = "abc-agent-retriever-v1"
```

서버를 다시 띄우고 `seed_index.py` 로 확인한다.

> **가중치는 커밋하지 않는다** (`ai/.gitignore` 의 `models/`).
> 팀에 넘길 때는 파일 전달이다. 대신 `results/eval_finetune.json` 과
> `log.md` 는 커밋한다 — 재현에 필요한 건 숫자와 절차이지 가중치가 아니다.

---

## 6. 뒷정리 ★

Jupyter 를 껐다고 프로세스가 죽지는 않는다. 반드시 확인한다.

```bash
nvidia-smi                    # 내 프로세스가 메모리를 잡고 있는지
ps -ef | grep $USER | grep python
kill -9 <PID>                 # 남아 있으면 정리
```

---

## 7. 모델이 바뀌면 백엔드에 알린다

파인튜닝으로 가중치가 바뀌면 **벡터 공간 자체가 달라진다.** 기존 인덱스의
벡터와 새 모델이 만든 질의 벡터는 비교할 수 없다.

- 응답의 `model_version` 이 `ko-sroberta-multitask-pretrained` → `abc-agent-retriever-v1`
- 백엔드에 **`POST /v1/index/agents` `op: "rebuild"`** 요청을 보내달라고 전달
- 스냅샷은 `model_version` 이 다르면 자동으로 버린다 (`app/index/store.py`)
