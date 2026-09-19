package com.aiblockclock.domain.auth;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class SessionTokenHasher {
    private static final String HASH_ALGORITHM = "SHA-256";

    public String hash(String rawSessionToken) {
        byte[] sessionTokenBytes = rawSessionToken.getBytes(StandardCharsets.UTF_8);

        try {
            MessageDigest messageDigest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = messageDigest.digest(sessionTokenBytes);

            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException exception) {
            CoreException coreException = new CoreException(ErrorType.INTERNAL_ERROR);
            coreException.initCause(exception);
            throw coreException;
        }
    }
}
