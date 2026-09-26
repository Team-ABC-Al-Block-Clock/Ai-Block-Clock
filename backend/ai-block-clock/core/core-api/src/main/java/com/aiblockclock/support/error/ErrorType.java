package com.aiblockclock.support.error;

import lombok.Getter;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorType {
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.COMMON_002,
            "서버에서 처리하지 못한 오류가 발생했습니다.", LogLevel.ERROR),

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, ErrorCode.COMMON_001,
            "요청 값이 올바르지 않습니다.", LogLevel.WARN),

    REQUESTER_NOT_IDENTIFIED(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_005,
            "요청자를 확인할 수 없습니다.", LogLevel.WARN),

    REQUESTER_NOT_IDENTIFIABLE(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_006,
            "요청자 정보가 올바르지 않습니다.", LogLevel.WARN),

    WALLET_AUTH_MESSAGE_CONCURRENTLY_MODIFIED(HttpStatus.CONFLICT, ErrorCode.AUTH_007,
            "인증 메시지가 다른 요청에 의해 변경되었습니다. 다시 발급받아 주세요.", LogLevel.WARN),

    WALLET_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_008,
            "지갑 서명이 올바르지 않습니다.", LogLevel.WARN),

    WALLET_AUTH_MESSAGE_EXPIRED(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_009,
            "지갑 인증 메시지가 만료되었습니다. 새 메시지를 발급받아 주세요.", LogLevel.WARN),

    WALLET_AUTH_MESSAGE_ALREADY_USED(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_010,
            "이미 사용한 지갑 인증 메시지입니다. 새 메시지를 발급받아 주세요.", LogLevel.WARN),

    WALLET_AUTH_MESSAGE_NOT_USABLE(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_011,
            "지갑 인증 메시지를 사용할 수 없습니다. 새 메시지를 발급받아 주세요.", LogLevel.WARN),

    WALLET_AUTH_MESSAGE_NOT_FOUND(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_012,
            "지갑 인증 메시지가 없습니다. 먼저 메시지를 발급받아 주세요.", LogLevel.WARN),

    USER_NICKNAME_LENGTH_INVALID(HttpStatus.BAD_REQUEST, ErrorCode.USER_002,
            "이름은 2~20자여야 합니다.", LogLevel.WARN),

    USER_INTRODUCTION_TOO_LONG(HttpStatus.BAD_REQUEST, ErrorCode.USER_002,
            "한 줄 소개는 40자를 넘을 수 없습니다.", LogLevel.WARN),

    USER_TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, ErrorCode.USER_003,
            "이용약관 및 개인정보처리방침에 동의해야 합니다.", LogLevel.WARN),

    USER_RECOVERY_NOTICE_NOT_CONFIRMED(HttpStatus.BAD_REQUEST, ErrorCode.USER_003,
            "지갑을 분실하면 계정을 복구할 수 없다는 점을 확인해야 합니다.", LogLevel.WARN),

    USER_ALREADY_ONBOARDED(HttpStatus.CONFLICT, ErrorCode.USER_006,
            "이미 가입을 마쳤습니다.", LogLevel.WARN),

    WORKFLOW_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.WORKFLOW_002,
            "워크플로우를 찾을 수 없습니다.", LogLevel.WARN),

    WORKFLOW_FORBIDDEN(HttpStatus.FORBIDDEN, ErrorCode.WORKFLOW_003,
            "워크플로우에 대한 권한이 없습니다.", LogLevel.WARN),

    WORKFLOW_GRAPH_CYCLE(HttpStatus.BAD_REQUEST, ErrorCode.WORKFLOW_005,
            "노드 연결에 순환 구조가 존재합니다.", LogLevel.WARN),

    WORKFLOW_NODE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, ErrorCode.WORKFLOW_006,
            "노드 수가 허용 범위를 넘었습니다.", LogLevel.WARN),

    WORKFLOW_DEPTH_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, ErrorCode.WORKFLOW_007,
            "연결 깊이가 허용 범위를 넘었습니다.", LogLevel.WARN),

    WORKFLOW_EXECUTION_IN_PROGRESS(HttpStatus.CONFLICT, ErrorCode.WORKFLOW_010,
            "실행이 진행 중인 워크플로우는 삭제할 수 없습니다.", LogLevel.WARN),

    WORKFLOW_DUPLICATE_NODE_KEY(HttpStatus.BAD_REQUEST, ErrorCode.WORKFLOW_013,
            "노드 키가 중복되었습니다.", LogLevel.WARN),

    WORKFLOW_DUPLICATE_EDGE_KEY(HttpStatus.BAD_REQUEST, ErrorCode.WORKFLOW_013,
            "연결선 키가 중복되었습니다.", LogLevel.WARN),

    WORKFLOW_DANGLING_EDGE(HttpStatus.BAD_REQUEST, ErrorCode.WORKFLOW_013,
            "연결선이 존재하지 않는 노드를 가리킵니다.", LogLevel.WARN),

    WORKFLOW_NODE_REFERENCE_MISMATCH(HttpStatus.BAD_REQUEST, ErrorCode.WORKFLOW_013,
            "노드 종류와 참조 정보가 맞지 않습니다.", LogLevel.WARN),

    WORKFLOW_GRAPH_NOT_TREE(HttpStatus.BAD_REQUEST, ErrorCode.WORKFLOW_013,
            "분기 노드가 아니면 갈라질 수 없고, 한 노드로 들어오는 선은 하나뿐입니다.", LogLevel.WARN),

    WORKFLOW_BRANCH_EDGES_INVALID(HttpStatus.BAD_REQUEST, ErrorCode.WORKFLOW_013,
            "분기 노드는 참 · 거짓 갈래를 하나씩 내고, 갈래 표시는 분기 노드에서 나가는 선에만 붙습니다.", LogLevel.WARN),

    WORKFLOW_BRANCH_CONDITION_INVALID(HttpStatus.BAD_REQUEST, ErrorCode.WORKFLOW_013,
            "분기 조건은 이 분기보다 앞에 놓인 작업 노드의 결과를 `<nodeKey>.<결과 안의 경로>` 로 가리켜야 합니다.",
            LogLevel.WARN),

    WORKFLOW_IO_NODE_DUPLICATED(HttpStatus.BAD_REQUEST, ErrorCode.WORKFLOW_013,
            "시작 노드와 끝 노드는 각각 하나만 놓을 수 있습니다.", LogLevel.WARN),

    WORKFLOW_IO_NODE_EDGE_INVALID(HttpStatus.BAD_REQUEST, ErrorCode.WORKFLOW_013,
            "시작 노드로는 선이 들어올 수 없고, 끝 노드에서는 선이 나갈 수 없습니다.", LogLevel.WARN),

    WORKFLOW_GRAPH_EMPTY(HttpStatus.BAD_REQUEST, ErrorCode.WORKFLOW_014,
            "노드를 하나 이상 놓아야 저장할 수 있습니다.", LogLevel.WARN),

    WORKFLOW_NODE_SOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.WORKFLOW_015,
            "배치할 노드나 공개된 에이전트를 찾을 수 없습니다.", LogLevel.WARN),

    WORKFLOW_REVISION_CONFLICT(HttpStatus.CONFLICT, ErrorCode.WORKFLOW_016,
            "워크플로우가 다른 요청에 의해 수정되었습니다.", LogLevel.WARN),

    WORKFLOW_NODE_TYPE_MISMATCH(HttpStatus.BAD_REQUEST, ErrorCode.WORKFLOW_017,
            "배치한 노드가 요청한 종류가 아닙니다.", LogLevel.WARN),

    NODE_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.NODE_001,
            "노드를 찾을 수 없습니다.", LogLevel.WARN),

    NODE_FORBIDDEN(HttpStatus.FORBIDDEN, ErrorCode.NODE_002,
            "노드에 대한 권한이 없습니다.", LogLevel.WARN),

    NODE_PROMPT_EMPTY(HttpStatus.BAD_REQUEST, ErrorCode.NODE_003,
            "프롬프트를 입력해야 합니다.", LogLevel.WARN),

    NODE_PLACED_IN_GRAPH(HttpStatus.CONFLICT, ErrorCode.NODE_004,
            "워크플로우나 에이전트에 배치된 노드는 삭제할 수 없습니다.", LogLevel.WARN),

    REF_FILE_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.NODE_005,
            "참고 자료를 찾을 수 없습니다.", LogLevel.WARN),

    REF_FILE_EMPTY(HttpStatus.BAD_REQUEST, ErrorCode.NODE_006,
            "빈 파일은 붙일 수 없습니다.", LogLevel.WARN),

    REF_FILE_TYPE_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, ErrorCode.NODE_006,
            "PDF · 텍스트 · 마크다운 · CSV · 이미지만 붙일 수 있습니다.", LogLevel.WARN),

    REF_FILE_TOTAL_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.NODE_007,
            "노드에 붙인 참고 자료의 합계가 10MB 를 넘습니다.", LogLevel.WARN),

    EXECUTION_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.EXECUTION_001,
            "실행을 찾을 수 없습니다.", LogLevel.WARN),

    EXECUTION_ENGINE_UNAVAILABLE(HttpStatus.BAD_GATEWAY, ErrorCode.EXECUTION_002,
            "실행을 맡기지 못했습니다. 잠시 후 다시 시도해 주세요.", LogLevel.WARN),

    EXECUTION_FILE_EMPTY(HttpStatus.BAD_REQUEST, ErrorCode.EXECUTION_003,
            "빈 파일은 올릴 수 없습니다.", LogLevel.WARN),

    EXECUTION_FILE_TYPE_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, ErrorCode.EXECUTION_003,
            "엑셀 또는 CSV 파일만 올릴 수 있습니다.", LogLevel.WARN),

    EXECUTION_FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.EXECUTION_004,
            "파일 크기가 10MB 를 넘습니다.", LogLevel.WARN),

    EXECUTION_RESULT_NOT_READY(HttpStatus.CONFLICT, ErrorCode.EXECUTION_001,
            "아직 끝나지 않은 실행입니다.", LogLevel.WARN),

    EXECUTION_RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.EXECUTION_001,
            "그 형식으로 받을 수 있는 결과가 없습니다.", LogLevel.WARN),

    EXECUTION_IO_NODE_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, ErrorCode.EXECUTION_005,
            "시작 노드와 끝 노드가 놓인 워크플로우는 아직 실행할 수 없습니다.", LogLevel.WARN),

    EXECUTION_BRANCH_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, ErrorCode.EXECUTION_005,
            "분기가 있는 워크플로우는 아직 실행할 수 없습니다.", LogLevel.WARN),

    EXECUTION_ALREADY_REQUESTED(HttpStatus.CONFLICT, ErrorCode.EXECUTION_006,
            "같은 실행 요청을 이미 처리하고 있습니다.", LogLevel.WARN),

    FILE_NOT_STORED(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.FILE_001,
            "올린 파일을 저장하지 못했습니다.", LogLevel.ERROR),

    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.FILE_002,
            "올린 파일이 10MB 를 넘습니다.", LogLevel.WARN),

    RECOMMEND_UNAVAILABLE(HttpStatus.BAD_GATEWAY, ErrorCode.RECOMMEND_001,
            "추천을 가져오지 못했습니다. 잠시 후 다시 시도해 주세요.", LogLevel.WARN),

    AGENT_WORKFLOW_EMPTY(HttpStatus.CONFLICT, ErrorCode.AGENT_001,
            "노드가 없는 워크플로우로는 에이전트를 만들 수 없습니다.", LogLevel.WARN),

    AGENT_FOREIGN_NODE_INCLUDED(HttpStatus.FORBIDDEN, ErrorCode.AGENT_002,
            "타인 소유의 노드 또는 에이전트가 포함되어 있습니다.", LogLevel.WARN),

    AGENT_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.AGENT_003,
            "공개된 에이전트를 찾을 수 없습니다.", LogLevel.WARN),

    AGENT_FORBIDDEN(HttpStatus.FORBIDDEN, ErrorCode.AGENT_006,
            "에이전트에 대한 권한이 없습니다.", LogLevel.WARN),

    AGENT_VERSION_ALREADY_PUBLISHED(HttpStatus.CONFLICT, ErrorCode.AGENT_007,
            "이미 공개된 버전은 변경할 수 없습니다.", LogLevel.WARN),

    AGENT_VERSION_NOT_NEWER(HttpStatus.CONFLICT, ErrorCode.AGENT_009,
            "이미 공개된 버전보다 이전 버전은 공개할 수 없습니다.", LogLevel.WARN),

    CHARGE_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.PAYMENT_001,
            "충전 요청을 찾을 수 없습니다.", LogLevel.WARN),

    CHARGE_REQUEST_FORBIDDEN(HttpStatus.FORBIDDEN, ErrorCode.PAYMENT_002,
            "다른 사용자의 충전 요청에는 접근할 수 없습니다.", LogLevel.WARN),

    CHARGE_TRANSACTION_ALREADY_USED(HttpStatus.CONFLICT, ErrorCode.PAYMENT_003,
            "이미 다른 사용자의 충전에 사용된 트랜잭션입니다.", LogLevel.WARN),

    CHARGE_REQUEST_ALREADY_EXISTS(HttpStatus.CONFLICT, ErrorCode.PAYMENT_003,
            "같은 거래의 충전 요청이 이미 존재합니다.", LogLevel.WARN),

    CHARGE_INVALID_STATE(HttpStatus.CONFLICT, ErrorCode.PAYMENT_004,
            "현재 상태에서는 충전 요청을 처리할 수 없습니다.", LogLevel.WARN),

    CHARGE_INVALID_AMOUNT(HttpStatus.BAD_REQUEST, ErrorCode.PAYMENT_005,
            "충전 수량이 올바르지 않습니다.", LogLevel.WARN),

    LEDGER_INVALID_TRANSACTION(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.PAYMENT_006,
            "원장 거래 구성이 올바르지 않습니다.", LogLevel.ERROR),

    CHARGE_REQUEST_CONCURRENTLY_MODIFIED(HttpStatus.CONFLICT, ErrorCode.PAYMENT_007,
            "충전 요청이 다른 처리에 의해 변경되었습니다.", LogLevel.WARN),

    ABC_BALANCE_INSUFFICIENT(HttpStatus.PAYMENT_REQUIRED, ErrorCode.PAYMENT_008,
            "ABC 잔액이 부족합니다. 충전한 뒤 다시 실행해 주세요.", LogLevel.WARN),

    CHARGE_SENDER_IS_CUSTODY(HttpStatus.FORBIDDEN, ErrorCode.PAYMENT_009,
            "입금 보관 지갑으로는 충전할 수 없습니다.", LogLevel.WARN),

    WITHDRAWAL_AMOUNT_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, ErrorCode.PAYMENT_010,
            "출금 금액이 한 번에 신청할 수 있는 범위를 벗어났습니다.", LogLevel.WARN),

    WITHDRAWAL_PAYOUT_INSUFFICIENT(HttpStatus.CONFLICT, ErrorCode.PAYMENT_011,
            "출금할 수 있는 정산금이 부족합니다.", LogLevel.WARN),

    WITHDRAWAL_ALREADY_IN_PROGRESS(HttpStatus.CONFLICT, ErrorCode.PAYMENT_012,
            "처리 중인 출금이 있어 새로 신청할 수 없습니다.", LogLevel.WARN),

    WITHDRAWAL_TOO_FREQUENT(HttpStatus.CONFLICT, ErrorCode.PAYMENT_013,
            "출금 신청 주기가 지나지 않았습니다.", LogLevel.WARN),

    WITHDRAWAL_ALREADY_REQUESTED(HttpStatus.CONFLICT, ErrorCode.PAYMENT_014,
            "같은 출금 신청이 처리 중입니다. 잠시 뒤 다시 확인해 주세요.", LogLevel.WARN),

    WITHDRAWAL_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.PAYMENT_015,
            "출금 신청을 찾을 수 없습니다.", LogLevel.WARN),

    WITHDRAWAL_INVALID_STATE(HttpStatus.CONFLICT, ErrorCode.PAYMENT_016,
            "현재 상태에서는 출금 신청을 바꿀 수 없습니다.", LogLevel.WARN);

    private final HttpStatus status;
    private final ErrorCode code;
    private final String message;
    private final LogLevel logLevel;

    ErrorType(HttpStatus status, ErrorCode code, String message, LogLevel logLevel) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.logLevel = logLevel;
    }
}
