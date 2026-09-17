package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.user.User;

public record WalletLogin(
        User user,
        AccessToken accessToken,
        IssuedRefreshToken refreshToken
) {
}
