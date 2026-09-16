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
@Table(name = "wallet_auth_message")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletAuthMessageEntity extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_address", nullable = false, length = 42)
    private String walletAddress;

    @Column(nullable = false, length = 64)
    private String nonce;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public static WalletAuthMessageEntity of(
            String walletAddress,
            String nonce,
            String message,
            Instant issuedAt,
            Instant expiresAt
    ) {
        WalletAuthMessageEntity entity = new WalletAuthMessageEntity();
        entity.walletAddress = walletAddress;
        entity.nonce = nonce;
        entity.message = message;
        entity.issuedAt = issuedAt;
        entity.expiresAt = expiresAt;
        return entity;
    }
}
