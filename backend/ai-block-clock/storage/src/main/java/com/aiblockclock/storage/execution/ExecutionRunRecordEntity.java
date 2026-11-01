package com.aiblockclock.storage.execution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

// 실행을 닫을 때 엔진이 마지막으로 말한 것의 원본이다. 실행마다 한 줄이고 고치지 않는다.
// 실행 ID 를 우리가 정해 넣으므로 늘 새 줄로 넣어, 같은 실행을 두 번 적으면 PK 가 막게 한다 (ExecutionFailureEntity 와 같다).
@Entity
@Table(name = "execution_run_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExecutionRunRecordEntity implements Persistable<UUID> {
    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "execution_id", length = 36, nullable = false, updatable = false)
    private UUID executionId;

    @Column(name = "run_id", length = 64, updatable = false)
    private String runId;

    @Column(name = "closed_by", length = 20, nullable = false, updatable = false)
    private String closedBy;

    @Column(name = "engine_status", length = 20, updatable = false)
    private String engineStatus;

    @Column(name = "response", columnDefinition = "mediumtext", updatable = false)
    private String response;

    @Column(name = "response_sha256", length = 64, updatable = false)
    private String responseSha256;

    @Column(name = "note", length = 255, updatable = false)
    private String note;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean stored;

    public ExecutionRunRecordEntity(UUID executionId, String runId, String closedBy, String engineStatus,
                                    String response, String responseSha256, String note, Instant recordedAt) {
        this.executionId = executionId;
        this.runId = fit(runId, 64);
        this.closedBy = closedBy;
        this.engineStatus = fit(engineStatus, 20);
        this.response = response;
        this.responseSha256 = responseSha256;
        this.note = fit(note, 255);
        this.recordedAt = recordedAt;
    }

    @Override
    public UUID getId() {
        return executionId;
    }

    @Override
    public boolean isNew() {
        return !stored;
    }

    @PostLoad
    @PostPersist
    void markStored() {
        stored = true;
    }

    private static String fit(String value, int length) {
        return value == null || value.length() <= length ? value : value.substring(0, length);
    }
}
