package com.aiblockclock.domain.auth;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class RefreshTokenHasher {
    private static final String HASH_ALGORITHM = "SHA-256";

    public String hash(String rawRefreshToken) {
        byte[] refreshTokenBytes = rawRefreshToken.getBytes(StandardCharsets.UTF_8);

        try {
            MessageDigest messageDigest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = messageDigest.digest(refreshTokenBytes);

            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException exception) {
            // Java가 필수로 지원하는 SHA-256이 없다면 실행 환경의 서버 오류로 처리한다.
            CoreException coreException = new CoreException(ErrorType.INTERNAL_ERROR);
            coreException.initCause(exception);
            throw coreException;
        }
    }
}
