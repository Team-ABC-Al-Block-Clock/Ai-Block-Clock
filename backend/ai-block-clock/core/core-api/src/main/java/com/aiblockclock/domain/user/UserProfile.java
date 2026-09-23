package com.aiblockclock.domain.user;

import com.aiblockclock.domain.auth.WalletNetwork;

public record UserProfile(
        User user,
        WalletNetwork network
) {
}
