"""실패 원인 안내 — 오류 코드 하나에서 책임 · 사용자 문구 · 성공률 반영 여부가 나온다.

계약 §9.2 표를 코드로 옮긴 것이다. 여기가 정산(PAY-011)과 등급(성공률)의 입구라
문구를 바꿀 때도 `fault` · `affects_success_rate`는 계약과 어긋나면 안 된다.

사용자에게 보이는 문구에는 **프롬프트 원문 · 모델 응답을 싣지 않는다**(SEC-001).
자세한 사유(`detail`)는 백엔드 · 로그용이고, 화면 문구는 `message` + `hint`다.
"""
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class ErrorSpec:
    fault: str                    # supplier | consumer | platform
    affects_success_rate: bool
    message: str                  # 화면에 그대로 띄우는 한 줄
    hint: str = ""                # 사용자가 다음에 할 수 있는 것


# 입력 검증 실패와 출력 검증 실패는 코드가 같고 책임이 다르다(계약 §9.2).
SCHEMA_INPUT = ErrorSpec(
    "consumer", False,
    "입력한 값이 이 블록이 받는 형식과 맞지 않습니다.",
    "입력 폼의 필수 항목과 값 형식을 확인한 뒤 다시 실행해 주세요. 사용료는 차감되지 않습니다.",
)
SCHEMA_OUTPUT = ErrorSpec(
    "supplier", True,
    "블록이 정해진 형식으로 결과를 만들지 못했습니다.",
    "두 번 고쳐 요청했지만 형식이 맞지 않았습니다. 이 블록의 사용료는 환원됩니다.",
)

CODES: dict[str, ErrorSpec] = {
    "SCHEMA_VALIDATION_FAILED": SCHEMA_OUTPUT,          # 기본은 출력 실패(공급자)
    "BRANCH_CONDITION_INVALID": ErrorSpec(
        "consumer", False, "분기 조건이 가리키는 값이 결과에 없습니다.",
        "조건에 쓴 항목이 앞 블록의 출력 형식에 있는지 확인해 주세요."),
    "PROMPT_TEMPLATE_INVALID": ErrorSpec(
        "supplier", False, "블록의 프롬프트가 요구하는 입력값을 이 자리에서 받을 수 없습니다.",
        "판매자가 프롬프트의 입력 틀({…})을 블록이 받는 형식에 맞게 고쳐야 합니다."),
    "INPUT_TOO_LARGE": ErrorSpec(
        "platform", False, "이 단계에 넘길 앞 결과와 참고 자료가 너무 큽니다.",
        "플랫폼의 입력 상한을 넘었습니다. 차감분은 전액 환원됩니다. 워크플로우를 나눠 실행해 주세요."),
    "REF_FILE_REJECTED": ErrorSpec(
        "supplier", False, "블록에 붙은 참고 자료를 쓸 수 없습니다.",
        "형식 또는 크기 제한을 넘었습니다. 판매자가 고쳐야 합니다."),
    "MODEL_TIMEOUT": ErrorSpec(
        "supplier", True, "블록이 제한 시간 안에 응답하지 못했습니다.",
        "잠시 후 다시 실행해 주세요. 이 블록의 사용료는 환원됩니다."),
    "MODEL_API_ERROR": ErrorSpec(
        "supplier", True, "AI 모델 호출에 실패했습니다.",
        "잠시 후 다시 실행해 주세요. 이 블록의 사용료는 환원됩니다."),
    "FILE_PARSE_FAILED": ErrorSpec(
        "consumer", False, "올린 파일을 읽지 못했습니다.",
        "지원 형식(.xlsx · .xls · .csv)인지, 표의 첫 줄이 열 이름인지 확인해 주세요."),
    "RATE_LIMITED": ErrorSpec(
        "platform", False, "요청이 몰려 지금은 처리하지 못했습니다.",
        "잠시 후 다시 실행해 주세요. 차감분은 환원됩니다."),
    "TEMPLATE_RENDER_FAILED": ErrorSpec(
        "platform", False, "결과는 만들어졌지만 서식 적용에 실패했습니다.",
        "JSON 결과는 정상입니다. 기본 서식으로 다시 받아 보세요."),
    "PDF_RENDER_FAILED": ErrorSpec(
        "platform", False, "결과 PDF를 만들지 못했습니다.",
        "JSON 결과는 정상입니다. 잠시 후 다시 내려받아 주세요."),
    "LLM_INIT_FAILED": ErrorSpec(
        "platform", False, "AI 실행 환경 설정에 문제가 있습니다.",
        "운영자에게 전달됐습니다. 차감분은 환원됩니다."),
    "RUNTIME_INTERNAL": ErrorSpec(
        "platform", False, "실행 중 내부 오류가 발생했습니다.",
        "운영자에게 전달됐습니다. 차감분은 환원됩니다."),
}

_UNKNOWN = ErrorSpec("platform", False, "알 수 없는 오류가 발생했습니다.", "운영자에게 전달됐습니다.")


def spec_of(code: str, fault: str | None = None) -> ErrorSpec:
    """코드의 규격. `fault`로 입력 실패(수요자)를 구분해 준다."""
    if code == "SCHEMA_VALIDATION_FAILED" and fault == "consumer":
        return SCHEMA_INPUT
    return CODES.get(code, _UNKNOWN)


def fault_of(code: str) -> str:
    return spec_of(code).fault


def describe(code: str, *, fault: str | None = None, detail: str = "") -> dict:
    """실행 결과에 실어 보낼 안내. `detail`은 백엔드 · 로그용이라 화면 문구와 분리한다."""
    s = spec_of(code, fault)
    return {
        "code": code,
        "fault": fault or s.fault,
        "affects_success_rate": s.affects_success_rate,
        "message": s.message,
        "hint": s.hint,
        "detail": detail[:500],
    }
