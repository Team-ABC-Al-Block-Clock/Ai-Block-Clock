"""판매자 프롬프트 읽기 — 마커 3블록과 자리표시자. 트랙 B 가 쓰는 작성 형식 그대로 해석한다.

트랙 B 의 프롬프트 파일은 설계 설명이 붙은 문서다. 모델에 들어가는 부분만 마커로 표시돼 있다.

    <!-- prompt:system -->   ```...```   역할 · 규칙 (시스템 프롬프트)
    <!-- prompt:user -->     ```...```   입력 틀. {brand_name} {market} 같은 자리표시자
    <!-- prompt:fewshot -->  ```...```   예시. user 뒤에 붙인다

자리표시자 채우는 규칙은 B 의 준수율 검사기(`ai/demo/check_compliance_brand.py`)와 같다 —
dict · list 는 JSON 으로, `comment` 가 없으면 "없음", 그 외 값이 없으면 오류.
같은 프롬프트가 검사기와 Runtime 에서 다르게 렌더되면 준수율 수치가 실행과 어긋나기 때문이다.

마커가 없는 텍스트는 그대로 시스템 프롬프트다(백엔드가 보내는 평문 · 09-19 이전 견본).
"""
from __future__ import annotations

import json
import re
from dataclasses import dataclass
from typing import Any

_MARK = re.compile(r"<!-- prompt:(\w+) -->\s*```[^\n]*\n(.*?)```", re.S)
_HOLDER = re.compile(r"\{(\w+)\}")
_DEFAULTS = {"comment": "없음"}          # 없어도 되는 자리표시자와 그 기본값


class PlaceholderMissing(Exception):
    """user 블록이 요구하는 값을 워크플로우가 주지 않는다 — 판매자 프롬프트 구성 오류."""

    def __init__(self, names: list[str]):
        super().__init__(", ".join(names))
        self.names = names


@dataclass(frozen=True)
class Prompt:
    system: str
    user_template: str | None       # 없으면 Runtime 기본 메시지(payload JSON)를 쓴다
    fewshot: str = ""

    @property
    def templated(self) -> bool:
        return self.user_template is not None

    def placeholders(self) -> list[str]:
        return sorted(set(_HOLDER.findall(self.user_template or "")))

    def render_user(self, values: dict[str, Any]) -> str:
        """자리표시자를 채운 user 메시지. fewshot 이 있으면 뒤에 붙인다."""
        assert self.user_template is not None
        missing = [n for n in self.placeholders() if n not in values and n not in _DEFAULTS]
        if missing:
            raise PlaceholderMissing(missing)

        def fill(match: re.Match) -> str:
            key = match.group(1)
            if key not in values:
                return _DEFAULTS[key]
            value = values[key]
            if isinstance(value, (dict, list)):
                return json.dumps(value, ensure_ascii=False)
            return str(value or _DEFAULTS.get(key, "없음"))

        user = _HOLDER.sub(fill, self.user_template)
        return user + ("\n\n" + self.fewshot if self.fewshot else "")


def parse(text: str) -> Prompt:
    """프롬프트 원문을 해석한다. 마커가 없으면 전체가 시스템 프롬프트다."""
    blocks = dict(_MARK.findall(text or ""))
    if "system" not in blocks:
        return Prompt(system=text or "", user_template=None)
    return Prompt(system=blocks["system"].strip(),
                  user_template=blocks.get("user", "").strip() or None,
                  fewshot=blocks.get("fewshot", "").strip())


def values_for(received: Any, *, input_payload: dict | None, comment: str) -> dict[str, Any]:
    """자리표시자에 넣을 값. 뒤에 오는 것이 앞의 것을 덮는다.

      1. 실행 입력(브리프)의 필드          — {brand_name} {category} …
      2. 받은 것이 dict 면 그 키들          — 병합이면 {market} {product}, 단일이면 그 payload 의 필드
      3. {input}                            — 받은 것 전체(단일이면 그 payload, 병합이면 port 묶음)
      4. 사용자 코멘트                      — {comment}
    """
    values: dict[str, Any] = {}
    if isinstance(input_payload, dict):
        values.update(input_payload)
    if isinstance(received, dict):
        values.update(received)
    if received is not None:
        values["input"] = received
    if comment:
        values["comment"] = comment
    return values
