"""E2E 전용 런타임 기동기. support/runtime.ts 가 ai/spike/linear-mvp 를 작업 폴더로 `uv run` 해서 부른다.

운영 코드는 그대로 쓰고, mock 모델 호출 한 번마다 E2E_MOCK_DELAY_SECONDS 초를 쉬게만 한다.
mock 은 곧바로 끝나 진행 단계가 화면에 비치지 않고, 도는 도중에 런타임을 죽일 틈도 없어서다.
"""
import os
import sys
import time

sys.path.insert(0, os.getcwd())

from src import llm  # noqa: E402

DELAY = float(os.getenv("E2E_MOCK_DELAY_SECONDS") or 0)

if DELAY > 0:
    _mock = llm.LLM._mock

    def _slow_mock(self, *args, **kwargs):
        time.sleep(DELAY)
        return _mock(self, *args, **kwargs)

    llm.LLM._mock = _slow_mock

import uvicorn  # noqa: E402

uvicorn.run("src.api:app", host=os.environ["E2E_RUNTIME_HOST"], port=int(os.environ["E2E_RUNTIME_PORT"]))
