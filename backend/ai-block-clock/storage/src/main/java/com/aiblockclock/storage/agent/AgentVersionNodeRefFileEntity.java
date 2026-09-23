package com.aiblockclock.storage.agent;

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

import java.util.UUID;

@Entity
@Table(name = "agent_version_node_ref_file")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentVersionNodeRefFileEntity extends BaseCreatedAtEntity {
    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", length = 36, nullable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "version_id", length = 36, nullable = false, updatable = false)
    private UUID versionId;

    @Column(name = "node_key", length = 64, nullable = false, updatable = false)
    private String nodeKey;

    @Column(name = "ordinal", nullable = false, updatable = false)
    private int ordinal;

    @Column(name = "file_key", length = 255, nullable = false, updatable = false)
    private String fileKey;

    @Column(name = "filename", length = 255, nullable = false, updatable = false)
    private String filename;

    @Column(name = "file_size", nullable = false, updatable = false)
    private long fileSize;

    public AgentVersionNodeRefFileEntity(UUID id, UUID versionId, String nodeKey, int ordinal,
                                         String fileKey, String filename, long fileSize) {
        this.id = id;
        this.versionId = versionId;
        this.nodeKey = nodeKey;
        this.ordinal = ordinal;
        this.fileKey = fileKey;
        this.filename = filename;
        this.fileSize = fileSize;
    }
}
