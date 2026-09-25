"""LLM 어댑터 — Gemini API 호출 · 429 백오프 · 토큰 기록 · mock 모드.

호출 경로는 환경변수로 정한다. 기본은 SSAFY GMS(Gen AI Management System)를 경유한다.
GMS는 Gemini 원본 API를 그대로 프록시하므로(헤더 x-goog-api-key 동일) google-genai SDK에
base_url 만 바꿔 끼운다. Google 직접 호출로 돌리려면 LLM_BASE_URL 을 비우면 된다.

    LLM_API_KEY    GMS 팀 키 (구 GEMINI_API_KEY 도 읽는다)
    LLM_BASE_URL   기본 https://gms.ssafy.io/gmsapi/generativelanguage.googleapis.com
    LLM_MODEL      기본 gemini-2.5-flash (구 GEMINI_MODEL 도 읽는다)

이 파일은 이후 ai/runtime/app/nodes/ 의 모델 어댑터 골격으로 재사용할 수 있게
외부 의존을 이 안에만 가둔다.
"""
from __future__ import annotations

import json
import os
import time
from dataclasses import dataclass

DEFAULT_MODEL = "gemini-2.5-flash"
DEFAULT_BASE_URL = "https://gms.ssafy.io/gmsapi/generativelanguage.googleapis.com"
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
    # 구조화 출력을 어떤 방식으로 받았나. response_schema | instruction | None(자유 텍스트)
    schema_mode: str | None = None


class LLM:
    def __init__(self, mock: bool = False, model: str | None = None, temperature: float = 0.2):
        self.mock = mock
        self.model = model or os.getenv("LLM_MODEL") or os.getenv("GEMINI_MODEL", DEFAULT_MODEL)
        self.temperature = temperature
        self._client = None
        self._schema_mode: str | None = None
        if not mock:
            api_key = os.getenv("LLM_API_KEY") or os.getenv("GEMINI_API_KEY")
            if not api_key:
                raise RuntimeError("LLM_API_KEY 가 없습니다. .env 를 만들거나 --mock 으로 실행하세요.")
            # 빈 문자열이면 Google 직접 호출(SDK 기본 주소). 미설정이면 GMS.
            base_url = os.getenv("LLM_BASE_URL", DEFAULT_BASE_URL).strip() or None
            from google import genai
            from google.genai import types

            self._types = types
            self.base_url = base_url
            self._client = genai.Client(
                api_key=api_key,
                http_options=types.HttpOptions(base_url=base_url, timeout=TIMEOUT_MS),
            )

    def generate(self, system: str, user: str, label: str = "",
                 response_schema: dict | None = None,
                 mock_json: dict | None = None,
                 attachments: list[tuple[bytes, str, str]] | None = None) -> LLMResult:
        """한 번 호출한다.

        `attachments` 는 `(bytes, mime, 파일명)` 목록 — 판매자 참고 파일(PDF)을 inline 으로
        함께 보낸다(계약 §7.1 `ref_files`). 텍스트 계열은 호출자가 본문에 넣으므로 여기 오지 않는다.

        `response_schema` 를 주면 **JSON 만** 받는다(구조화 출력). 스키마는 카탈로그가
        만들어 준 OpenAPI 부분집합이다(`src/catalog.response_schema`).
        프록시나 모델이 스키마를 거부하면 지시문 방식으로 한 번 더 시도한다 — 받는 쪽
        검증은 어차피 코드가 하므로(EXE-009) 어느 방식이든 결과는 같게 취급된다.

        `mock_json` 은 mock 모드에서 돌려줄 payload 다. 카탈로그 샘플을 넣으면
        LLM 없이도 스키마를 만족하는 JSON 이 나온다.
        """
        if self.mock:
            return self._mock(system, user, label, mock_json, attachments)

        types = self._types
        config = self._config(system, response_schema)
        contents = user if not attachments else (
            [types.Part.from_bytes(data=data, mime_type=mime) for data, mime, _ in attachments] + [user])
        retries = 0
        while True:
            start = time.perf_counter()
            try:
                resp = self._client.models.generate_content(model=self.model, contents=contents, config=config)
                usage = getattr(resp, "usage_metadata", None)
                return LLMResult(
                    text=resp.text or "",
                    model=self.model,
                    input_tokens=getattr(usage, "prompt_token_count", 0) or 0,
                    output_tokens=getattr(usage, "candidates_token_count", 0) or 0,
                    duration_ms=int((time.perf_counter() - start) * 1000),
                    retries=retries,
                    schema_mode=self._schema_mode,
                )
            except Exception as e:  # google.genai.errors.APIError 등
                code = getattr(e, "code", None) or getattr(e, "status_code", None)
                if response_schema is not None and _rejects_schema(code, e):
                    # 스키마를 거부하는 경로(구형 프록시 등)에서는 지시문으로 낮춰 한 번 더
                    print(f"  ! {label} 구조화 출력 거부(HTTP {code}) — 지시문 방식으로 전환")
                    system = system + _json_instruction(response_schema)
                    config = self._config(system, None)
                    response_schema = None
                    self._schema_mode = "instruction"
                    continue
                if code in RETRY_STATUS and retries < MAX_RETRIES:
                    retries += 1
                    print(f"  ! {label} HTTP {code} — {RETRY_WAIT_SEC}s 대기 후 재시도 {retries}/{MAX_RETRIES}")
                    time.sleep(RETRY_WAIT_SEC)
                    continue
                raise

    def _config(self, system: str, response_schema: dict | None):
        types = self._types
        kwargs = dict(
            system_instruction=system,
            temperature=self.temperature,
            # 도구를 안 쓰는데 SDK가 자동 함수 호출 경고를 찍는다. 명시적으로 끈다.
            automatic_function_calling=types.AutomaticFunctionCallingConfig(disable=True),
        )
        if response_schema is not None:
            kwargs["response_mime_type"] = "application/json"
            kwargs["response_schema"] = response_schema
            self._schema_mode = "response_schema"
        return types.GenerateContentConfig(**kwargs)

    def _mock(self, system: str, user: str, label: str, mock_json: dict | None = None,
              attachments: list[tuple[bytes, str, str]] | None = None) -> LLMResult:
        if mock_json is not None:
            # 구조화 출력 자리. 카탈로그 샘플을 그대로 돌려줘 검증 · 봉투 조립까지 실제 코드가 돈다
            text = json.dumps(mock_json, ensure_ascii=False, indent=2)
            return LLMResult(text=text, model="mock", input_tokens=len(user) // 2,
                             output_tokens=len(text) // 2, duration_ms=1, mock=True,
                             schema_mode="response_schema")
        head = user.strip().replace("\n", " ")[:300]
        text = (
            f"## [MOCK {label or self.model}]\n\n"
            f"시스템 프롬프트 {len(system)}자, 입력 {len(user)}자를 받았습니다.\n\n"
            f"입력 앞부분: {head} …\n\n"
            + (f"첨부 {len(attachments)}개: {', '.join(n for _, _, n in attachments)}\n\n" if attachments else "")
            +
            "| 항목 | 값 |\n| --- | --- |\n| 모드 | mock |\n| 한글 | 정상 |\n"
        )
        return LLMResult(text=text, model="mock", input_tokens=len(user) // 2,
                         output_tokens=len(text) // 2, duration_ms=1, mock=True)


def _rejects_schema(code: object, exc: Exception) -> bool:
    """구조화 출력 자체를 거부한 응답인가. 400 · INVALID_ARGUMENT 만 해당한다."""
    if code == 400:
        return True
    text = f"{exc}".lower()
    return "invalid_argument" in text or "response_schema" in text


_JSON_RULE = """

# 출력 형식
아래 JSON Schema 를 만족하는 **JSON 객체 하나만** 출력한다. 설명 · 인사말 · 코드블록 표시를 붙이지 않는다.
"""


def _json_instruction(schema: dict) -> str:
    return _JSON_RULE + json.dumps(schema, ensure_ascii=False)
