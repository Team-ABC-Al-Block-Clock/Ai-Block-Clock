package com.aiblockclock.domain.auth;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;

@Component
public class SessionTokenGenerator {
    private static final int SESSION_TOKEN_BYTE_LENGTH = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        byte[] randomBytes = new byte[SESSION_TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);

        return HexFormat.of().formatHex(randomBytes);
    }
}
