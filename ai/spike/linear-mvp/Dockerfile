# AI Runtime (선형 워크플로우 스파이크) 컨테이너 이미지
#
# 백엔드(backend/ai-block-clock/Dockerfile)와 달리 target 을 나누지 않는다.
# 파이썬은 컴파일 산출물이 없어 CI 가 미리 만들 jar 같은 것이 없기 때문이다.
#
# 한글 폰트를 apt 로 설치한다. PDF 렌더러가 TTF 를 이미지 안에서 찾아야 하는데,
# 폰트 파일(수 MB)을 저장소에 커밋하지 않으려는 것이다.
# 경로는 src/pdf.py 의 FONT_CANDIDATES 첫 항목과 맞춰져 있다.
#
# 빌드 컨텍스트는 ai/ 다 (무시 목록은 ai/.dockerignore).
#   cd ai/spike/linear-mvp && docker build -f Dockerfile -t ai-block-clock-ai-runtime ../..
# 공용 프롬프트(ai/prompts, 트랙 B 작성)를 함께 넣기 위해서다. 견본 워크플로우가
# `../../prompts/brand/…` 로 가리키는데, /app 의 두 단계 위는 / 이므로 /prompts 에 두면
# 로컬(ai/spike/linear-mvp → ai/prompts)과 같은 상대 경로가 컨테이너에서도 맞는다.

FROM ghcr.io/astral-sh/uv:python3.11-bookworm-slim

# fonts-nanum : PDF 한글 렌더링용 TTF (/usr/share/fonts/truetype/nanum/)
# curl        : compose 의 healthcheck 가 사용
RUN apt-get update \
    && apt-get install -y --no-install-recommends fonts-nanum curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 의존성만 먼저 설치해 레이어를 캐시한다. 소스가 바뀌어도 이 레이어는 재사용된다.
COPY spike/linear-mvp/pyproject.toml spike/linear-mvp/uv.lock ./
RUN uv sync --frozen --no-install-project

COPY spike/linear-mvp/ ./
COPY prompts/ /prompts/
RUN uv sync --frozen

# 실행 산출물 디렉터리. 컨테이너를 지우면 함께 사라진다.
# 결과물의 원본은 백엔드 스토리지이므로 볼륨을 잡지 않는다.
RUN mkdir -p out

ENV PYTHONUNBUFFERED=1 \
    PYTHONIOENCODING=utf-8

EXPOSE 8000

CMD ["uv", "run", "uvicorn", "src.api:app", "--host", "0.0.0.0", "--port", "8000"]
