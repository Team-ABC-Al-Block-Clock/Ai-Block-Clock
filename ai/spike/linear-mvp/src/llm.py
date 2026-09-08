"""LLM 어댑터 — Gemini API(무료 등급) 호출 · 429 백오프 · 토큰 기록 · mock 모드.

이 파일은 이후 ai/runtime/app/nodes/ 의 모델 어댑터 골격으로 재사용할 수 있게
외부 의존을 이 안에만 가둔다.
"""
from __future__ import annotations

import os
import time
from dataclasses import dataclass

DEFAULT_MODEL = "gemini-2.5-flash"
TIMEOUT_MS = 60_000
RETRY_STATUS = {429, 503}
RETRY_WAIT_SEC = 10
MAX_RETRIES = 2


@dataclass
class LLMResult:
    text: str
    model: str
    input_tokens: int
    output_tokens: int
    duration_ms: int
    retries: int = 0
    mock: bool = False


class LLM:
    def __init__(self, mock: bool = False, model: str | None = None, temperature: float = 0.2):
        self.mock = mock
        self.model = model or os.getenv("GEMINI_MODEL", DEFAULT_MODEL)
        self.temperature = temperature
        self._client = None
        if not mock:
            api_key = os.getenv("GEMINI_API_KEY")
            if not api_key:
                raise RuntimeError("GEMINI_API_KEY 가 없습니다. .env 를 만들거나 --mock 으로 실행하세요.")
            from google import genai
            from google.genai import types

            self._types = types
            self._client = genai.Client(api_key=api_key, http_options=types.HttpOptions(timeout=TIMEOUT_MS))

    def generate(self, system: str, user: str, label: str = "") -> LLMResult:
        if self.mock:
            return self._mock(system, user, label)

        types = self._types
        config = types.GenerateContentConfig(system_instruction=system, temperature=self.temperature)
        retries = 0
        while True:
            start = time.perf_counter()
            try:
                resp = self._client.models.generate_content(model=self.model, contents=user, config=config)
                usage = getattr(resp, "usage_metadata", None)
                return LLMResult(
                    text=resp.text or "",
                    model=self.model,
                    input_tokens=getattr(usage, "prompt_token_count", 0) or 0,
                    output_tokens=getattr(usage, "candidates_token_count", 0) or 0,
                    duration_ms=int((time.perf_counter() - start) * 1000),
                    retries=retries,
                )
            except Exception as e:  # google.genai.errors.APIError 등
                code = getattr(e, "code", None) or getattr(e, "status_code", None)
                if code in RETRY_STATUS and retries < MAX_RETRIES:
                    retries += 1
                    print(f"  ! {label} HTTP {code} — {RETRY_WAIT_SEC}s 대기 후 재시도 {retries}/{MAX_RETRIES}")
                    time.sleep(RETRY_WAIT_SEC)
                    continue
                raise

    def _mock(self, system: str, user: str, label: str) -> LLMResult:
        head = user.strip().replace("\n", " ")[:300]
        text = (
            f"## [MOCK {label or self.model}]\n\n"
            f"시스템 프롬프트 {len(system)}자, 입력 {len(user)}자를 받았습니다.\n\n"
            f"입력 앞부분: {head} …\n\n"
            "| 항목 | 값 |\n| --- | --- |\n| 모드 | mock |\n| 한글 | 정상 |\n"
        )
        return LLMResult(text=text, model="mock", input_tokens=len(user) // 2,
                         output_tokens=len(text) // 2, duration_ms=1, mock=True)
