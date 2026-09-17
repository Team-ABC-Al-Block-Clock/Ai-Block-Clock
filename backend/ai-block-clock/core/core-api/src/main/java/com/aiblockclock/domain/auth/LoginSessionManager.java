package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.user.UserReader;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class LoginSessionManager {
    private final UserReader userReader;
    private final LoginSessionWriter loginSessionWriter;
    private final RefreshTokenWriter refreshTokenWriter;

    @Transactional
    public LoginSession create(UserId userId, String refreshTokenHash, Instant expiresAt) {
        userReader.findByIdForUpdate(userId)
                .orElseThrow(() -> new CoreException(ErrorType.REQUESTER_NOT_IDENTIFIED));
        loginSessionWriter.modifyAllAsRevoked(userId);

        LoginSession session = loginSessionWriter.append(userId, expiresAt);
        refreshTokenWriter.append(session.id(), refreshTokenHash, expiresAt);

        return session;
    }
}
