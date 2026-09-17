package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.user.User;

public record WalletLoginSession(
        User user,
        LoginSession session,
        AccessToken accessToken
) {
}
