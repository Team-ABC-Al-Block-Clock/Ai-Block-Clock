package com.aiblockclock.domain.user;

import com.aiblockclock.domain.payment.LedgerAccountWriter;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UserRegistrationManager {
    private final UserWriter userWriter;
    private final LedgerAccountWriter ledgerAccountWriter;

    @Transactional
    public User register(String walletAddress) {
        User user = userWriter.create(walletAddress);

        ledgerAccountWriter.createAbcBalance(user.id());

        return user;
    }
}
