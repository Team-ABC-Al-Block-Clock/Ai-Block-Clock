package com.aiblockclock.domain.auth;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.web3j.crypto.Keys;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class WalletAuthMessageGenerator {

    private static final String MESSAGE_TEMPLATE = """
            %s://%s wants you to sign in with your Ethereum account:
            %s

            Sign in to ABC.

            URI: %s
            Version: 1
            Chain ID: %s
            Nonce: %s
            Issued At: %s
            Expiration Time: %s""";

    private final WalletAuthProperties properties;
    private final WalletNonceGenerator walletNonceGenerator;
    private final Clock clock;

    public WalletAuthMessage generate(String walletAddress) {
        String normalizedWalletAddress = walletAddress.toLowerCase(Locale.ROOT);
        String checksumWalletAddress = Keys.toChecksumAddress(normalizedWalletAddress);
        String nonce = walletNonceGenerator.generate();
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant expiresAt = issuedAt.plus(properties.messageTtl());

        String message = MESSAGE_TEMPLATE.formatted(
                properties.uri().getScheme(),
                properties.domain(),
                checksumWalletAddress,
                properties.uri(),
                properties.chainId(),
                nonce,
                issuedAt,
                expiresAt
        );

        return new WalletAuthMessage(normalizedWalletAddress, nonce, message, issuedAt, expiresAt, null);
    }
}
