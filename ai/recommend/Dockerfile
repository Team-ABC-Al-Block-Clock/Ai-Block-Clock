# 추천 서비스(runtime-recommend) 컨테이너
#
#   빌드   docker build -t abc-recommend:local ai/recommend
#   실행   docker run --rm -p 8100:8100 \
#            -v /opt/abc/models:/app/models:ro \
#            -v /opt/abc/index:/app/index \
#            abc-recommend:local
#
# 컨테이너 이름 ai-block-clock-recommend · 포트 8100 은 로드맵 09-18 관문이다.
# 백엔드가 컨테이너 이름으로 부르므로 compose 쪽에서 맞춘다.

# ★ 3.12 여야 한다. 3.11 로 하면 numpy 2.5.x 가 설치되지 않는다 (Requires-Python >=3.12).
#   로컬 venv 도 3.12.12 다. 개발 환경과 컨테이너의 파이썬이 다르면
#   "로컬에선 되는데 컨테이너에선 안 되는" 차이가 조용히 생긴다.
FROM python:3.12-slim

# ── 환경 ────────────────────────────────────────────────────
#   PYTHONDONTWRITEBYTECODE  읽기 전용으로 돌려도 .pyc 쓰기로 실패하지 않는다
#   PYTHONUNBUFFERED         print 가 즉시 나온다. 없으면 docker logs 가 비어 보인다
#   HF_HUB_OFFLINE           ★ 아래 설명
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    HF_HUB_OFFLINE=1 \
    HF_HOME=/app/.cache/huggingface

# ★ HF_HUB_OFFLINE=1 인 이유.
#   sentence-transformers 는 기동할 때마다 huggingface.co 에 모델이 갱신됐는지
#   확인하러 간다. 로컬은 인터넷이 되니 조용히 넘어가지만, EC2 에서 이 요청이
#   막히거나 느리면 **컨테이너가 기동 도중에 멈춘다.** 우리는 가중치를 볼륨으로
#   직접 넣어 주므로 조회할 이유가 없다. 로컬에서는 절대 안 보이는 사고라
#   처음부터 꺼 둔다.

WORKDIR /app

# ── 1단계: torch CPU 전용 휠 ─────────────────────────────────
# requirements.txt 보다 먼저, 별도 레이어로 깐다.
#   · CPU 휠을 먼저 깔아 두면 뒤에서 torch==2.14.0 이 이미 만족돼 다시 안 깔린다
#   · 레이어가 따로라 requirements.txt 만 바뀌었을 때 torch 를 다시 안 받는다
#     (torch 는 200MB 대라 재설치 시간이 눈에 띄게 길다)
RUN pip install --no-cache-dir \
        --index-url https://download.pytorch.org/whl/cpu \
        torch==2.14.0

# ── 2단계: 나머지 의존성 ─────────────────────────────────────
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# ── 3단계: 애플리케이션 ──────────────────────────────────────
# 소스는 마지막에 복사한다. 코드만 고쳤을 때 위 두 레이어를 캐시로 재사용한다.
COPY app ./app
COPY data ./data

# 가중치(models/)와 스냅샷(index/)은 이미지에 넣지 않고 볼륨으로 받는다.
# 마운트가 없어도 기동은 되어야 하므로 빈 폴더를 만들어 둔다.
#   · models/ 가 비면 config.py 가 사전학습 모델로 넘어간다 → HF_HUB_OFFLINE 때문에
#     받지 못하고 실패한다. 즉 **가중치 마운트를 빠뜨리면 조용히 다른 모델로
#     도는 게 아니라 기동 단계에서 명확히 죽는다.** 의도한 동작이다.
RUN mkdir -p /app/models /app/index

ENV ABC_HYBRID=weighted \
    ABC_SNAPSHOT_PATH=/app/index/index_snapshot.npz

EXPOSE 8100

# 09-18 관문이 GET /health 200 이므로 헬스체크도 같은 것을 본다.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8100/health',timeout=4).status==200 else 1)"
# start-period 가 90초인 이유: 모델 로딩에 6~8초(09-08 실측)가 걸리고 EC2 의
# 느린 디스크에서는 더 걸린다. 짧게 잡으면 기동 중인 컨테이너를 죽여 버린다.

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8100"]
