package com.aiblockclock.storage.execution;

import com.aiblockclock.storage.audit.BaseCreatedAtEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

// 브로커로 넘길 실행 맡기기(SubmitRun)다. 실행마다 한 줄이다. payload 는 보낼 JSON 본문 그대로다.
@Entity
@Table(name = "execution_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExecutionOutboxEntity extends BaseCreatedAtEntity {
    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "execution_id", length = 36, nullable = false, updatable = false)
    private UUID executionId;

    @Column(name = "payload", columnDefinition = "mediumtext", nullable = false, updatable = false)
    private String payload;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    public ExecutionOutboxEntity(UUID executionId, String payload) {
        this.executionId = executionId;
        this.payload = payload;
    }

    public void markDispatched(Instant dispatchedAt) {
        this.dispatchedAt = dispatchedAt;
    }
}
