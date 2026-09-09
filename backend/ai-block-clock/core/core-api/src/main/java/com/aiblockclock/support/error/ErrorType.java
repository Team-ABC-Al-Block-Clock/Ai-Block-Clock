package com.aiblockclock.support.error;

import lombok.Getter;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorType {
    WORKFLOW_FORBIDDEN(HttpStatus.FORBIDDEN, ErrorCode.WORKFLOW_003,
            "워크플로우에 대한 권한이 없습니다.", LogLevel.WARN),

    WORKFLOW_REVISION_CONFLICT(HttpStatus.CONFLICT, ErrorCode.WORKFLOW_005,
            "워크플로우가 다른 요청에 의해 수정되었습니다.", LogLevel.WARN),

    WORKFLOW_GRAPH_CYCLE(HttpStatus.BAD_REQUEST, ErrorCode.WORKFLOW_006,
            "노드 연결에 순환 구조가 존재합니다.", LogLevel.WARN),

    WORKFLOW_NODE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, ErrorCode.WORKFLOW_007,
            "노드 수가 허용 범위를 넘었습니다.", LogLevel.WARN),

    WORKFLOW_DEPTH_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, ErrorCode.WORKFLOW_007,
            "연결 깊이가 허용 범위를 넘었습니다.", LogLevel.WARN),

    WORKFLOW_DUPLICATE_NODE_KEY(HttpStatus.BAD_REQUEST, ErrorCode.WORKFLOW_008,
            "노드 키가 중복되었습니다.", LogLevel.WARN),

    WORKFLOW_DUPLICATE_EDGE_KEY(HttpStatus.BAD_REQUEST, ErrorCode.WORKFLOW_008,
            "연결선 키가 중복되었습니다.", LogLevel.WARN),

    WORKFLOW_EDGE_SELF_LOOP(HttpStatus.BAD_REQUEST, ErrorCode.WORKFLOW_008,
            "연결선이 자기 자신을 가리킵니다.", LogLevel.WARN),

    WORKFLOW_DANGLING_EDGE(HttpStatus.BAD_REQUEST, ErrorCode.WORKFLOW_008,
            "연결선이 존재하지 않는 노드를 가리킵니다.", LogLevel.WARN),

    WORKFLOW_NODE_REFERENCE_MISMATCH(HttpStatus.BAD_REQUEST, ErrorCode.WORKFLOW_008,
            "노드 종류와 참조 정보가 맞지 않습니다.", LogLevel.WARN),

    AGENT_WORKFLOW_EMPTY(HttpStatus.CONFLICT, ErrorCode.AGENT_001,
            "노드가 없는 워크플로우는 캡슐화할 수 없습니다.", LogLevel.WARN),

    AGENT_WORKFLOW_NOT_VALIDATED(HttpStatus.CONFLICT, ErrorCode.AGENT_001,
            "검증이 완료되지 않은 워크플로우입니다.", LogLevel.WARN),

    AGENT_FOREIGN_NODE_INCLUDED(HttpStatus.FORBIDDEN, ErrorCode.AGENT_002,
            "타인 소유의 노드 또는 에이전트가 포함되어 있습니다.", LogLevel.WARN),

    AGENT_FORBIDDEN(HttpStatus.FORBIDDEN, ErrorCode.AGENT_006,
            "에이전트에 대한 권한이 없습니다.", LogLevel.WARN),

    AGENT_VERSION_ALREADY_PUBLISHED(HttpStatus.CONFLICT, ErrorCode.AGENT_007,
            "이미 공개된 버전은 변경할 수 없습니다.", LogLevel.WARN);

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
