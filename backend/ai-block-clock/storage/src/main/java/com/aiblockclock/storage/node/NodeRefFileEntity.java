package com.aiblockclock.storage.node;

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
@Table(name = "node_ref_file")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NodeRefFileEntity extends BaseCreatedAtEntity {
    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", length = 36, nullable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "node_definition_id", length = 36, nullable = false, updatable = false)
    private UUID nodeDefinitionId;

    @Column(name = "ordinal", nullable = false, updatable = false)
    private int ordinal;

    @Column(name = "file_key", length = 255, nullable = false, updatable = false)
    private String fileKey;

    @Column(name = "filename", length = 255, nullable = false, updatable = false)
    private String filename;

    @Column(name = "file_size", nullable = false, updatable = false)
    private long fileSize;

    public NodeRefFileEntity(UUID id, UUID nodeDefinitionId, int ordinal,
                             String fileKey, String filename, long fileSize) {
        this.id = id;
        this.nodeDefinitionId = nodeDefinitionId;
        this.ordinal = ordinal;
        this.fileKey = fileKey;
        this.filename = filename;
        this.fileSize = fileSize;
    }
}
