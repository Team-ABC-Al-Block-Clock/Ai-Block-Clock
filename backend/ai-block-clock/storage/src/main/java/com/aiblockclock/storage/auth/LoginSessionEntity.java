package com.aiblockclock.storage.auth;

import com.aiblockclock.storage.audit.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "login_session")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginSessionEntity extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public static LoginSessionEntity of(Long userId, Instant expiresAt) {
        LoginSessionEntity entity = new LoginSessionEntity();
        entity.userId = userId;
        entity.expiresAt = expiresAt;
        return entity;
    }
}
