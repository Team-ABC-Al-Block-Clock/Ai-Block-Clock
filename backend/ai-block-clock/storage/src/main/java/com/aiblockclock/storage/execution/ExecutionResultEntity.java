package com.aiblockclock.storage.execution;

import com.aiblockclock.enums.execution.ExecutionResultFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "execution_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExecutionResultEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "execution_id", length = 36, nullable = false, updatable = false)
    private UUID executionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", length = 20, nullable = false, updatable = false)
    private ExecutionResultFormat format;

    @Column(name = "file_key", length = 128, nullable = false)
    private String fileKey;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    public ExecutionResultEntity(UUID executionId, ExecutionResultFormat format, String fileKey, long fileSize) {
        this.executionId = executionId;
        this.format = format;
        this.fileKey = fileKey;
        this.fileSize = fileSize;
    }
}
