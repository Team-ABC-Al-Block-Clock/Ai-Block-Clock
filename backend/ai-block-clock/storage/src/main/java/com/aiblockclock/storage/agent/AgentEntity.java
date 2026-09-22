package com.aiblockclock.storage.agent;

import com.aiblockclock.storage.audit.BaseTimeEntity;

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
@Table(name = "agent")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentEntity extends BaseTimeEntity {
    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", length = 36, nullable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "name", length = 40, nullable = false)
    private String name;

    @Column(name = "summary", length = 80, nullable = false)
    private String summary;

    @Column(name = "description", length = 2000, nullable = false)
    private String description;

    @Column(name = "usage_example", length = 500)
    private String usageExample;

    @Column(name = "onboarding_message", length = 20)
    private String onboardingMessage;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "public_version_id", length = 36)
    private UUID publicVersionId;

    public AgentEntity(UUID id, Long ownerId, String name, String summary, String description,
                       String usageExample, String onboardingMessage, String thumbnailUrl) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
        this.summary = summary;
        this.description = description;
        this.usageExample = usageExample;
        this.onboardingMessage = onboardingMessage;
        this.thumbnailUrl = thumbnailUrl;
    }

    public void publish(UUID publicVersionId) {
        this.publicVersionId = publicVersionId;
    }
}
