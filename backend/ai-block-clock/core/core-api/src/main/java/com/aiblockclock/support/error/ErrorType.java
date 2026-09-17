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

    LOGIN_SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_013,
            "로그인 세션이 만료되었습니다. 다시 로그인해 주세요.", LogLevel.WARN),

    LOGIN_SESSION_REVOKED(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_014,
            "종료된 로그인 세션입니다. 다시 로그인해 주세요.", LogLevel.WARN),

    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_015,
            "Refresh Token이 만료되었습니다. 다시 로그인해 주세요.", LogLevel.WARN),

    REFRESH_TOKEN_ALREADY_USED(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_016,
            "이미 사용한 Refresh Token입니다.", LogLevel.WARN),

    REFRESH_TOKEN_NOT_USABLE(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_017,
            "Refresh Token을 사용할 수 없습니다.", LogLevel.WARN),

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

    WORKFLOW_SOURCE_OF_AGENT_VERSION(HttpStatus.CONFLICT, ErrorCode.WORKFLOW_009,
            "에이전트 버전의 출처인 워크플로우는 삭제할 수 없습니다.", LogLevel.WARN),

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

    WORKFLOW_GRAPH_NOT_LINEAR(HttpStatus.BAD_REQUEST, ErrorCode.WORKFLOW_013,
            "노드는 한 줄로 이어져야 합니다.", LogLevel.WARN),

    WORKFLOW_GRAPH_EMPTY(HttpStatus.BAD_REQUEST, ErrorCode.WORKFLOW_014,
            "노드를 하나 이상 놓아야 저장할 수 있습니다.", LogLevel.WARN),

    WORKFLOW_NODE_SOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.WORKFLOW_015,
            "배치할 노드나 공개된 에이전트를 찾을 수 없습니다.", LogLevel.WARN),

    WORKFLOW_REVISION_CONFLICT(HttpStatus.CONFLICT, ErrorCode.WORKFLOW_016,
            "워크플로우가 다른 요청에 의해 수정되었습니다.", LogLevel.WARN),

    AGENT_WORKFLOW_EMPTY(HttpStatus.CONFLICT, ErrorCode.AGENT_001,
            "노드가 없는 워크플로우로는 에이전트를 만들 수 없습니다.", LogLevel.WARN),

    AGENT_WORKFLOW_NOT_VALIDATED(HttpStatus.CONFLICT, ErrorCode.AGENT_001,
            "검증이 완료되지 않은 워크플로우입니다.", LogLevel.WARN),

    AGENT_FOREIGN_NODE_INCLUDED(HttpStatus.FORBIDDEN, ErrorCode.AGENT_002,
            "타인 소유의 노드 또는 에이전트가 포함되어 있습니다.", LogLevel.WARN),

    AGENT_FORBIDDEN(HttpStatus.FORBIDDEN, ErrorCode.AGENT_006,
            "에이전트에 대한 권한이 없습니다.", LogLevel.WARN),

    AGENT_VERSION_ALREADY_PUBLISHED(HttpStatus.CONFLICT, ErrorCode.AGENT_007,
            "이미 공개된 버전은 변경할 수 없습니다.", LogLevel.WARN),

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
            "충전 요청이 다른 처리에 의해 변경되었습니다.", LogLevel.WARN);

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
