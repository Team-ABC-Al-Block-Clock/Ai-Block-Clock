package com.aiblockclock.domain.auth;

import com.aiblockclock.storage.auth.WalletAuthMessageRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WalletAuthMessageReader {
    private final WalletAuthMessageRepository walletAuthMessageRepository;

    public Optional<WalletAuthMessage> findByWalletAddress(String walletAddress) {
        String normalizedWalletAddress = walletAddress.toLowerCase(Locale.ROOT);

        return walletAuthMessageRepository.findByWalletAddress(normalizedWalletAddress)
                .map(WalletAuthMessageEntityConverter::toDomain);
    }
}
