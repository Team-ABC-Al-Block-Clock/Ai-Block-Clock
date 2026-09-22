package com.aiblockclock.domain.auth;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
@RequiredArgsConstructor
public class WalletAuthenticator {
    private final WalletAuthMessageReader walletAuthMessageReader;
    private final WalletAuthMessageWriter walletAuthMessageWriter;
    private final WalletSignatureValidator walletSignatureValidator;
    private final Clock clock;

    /** 인증 메시지 확인부터 사용 처리까지 마치고, 확인된 지갑 주소를 돌려준다. */
    public String authenticate(String walletAddress, String signature) {
        WalletAuthMessage authMessage = walletAuthMessageReader.findByWalletAddress(walletAddress)
                .orElseThrow(() -> new CoreException(ErrorType.WALLET_AUTH_MESSAGE_NOT_FOUND));
        authMessage.checkUsable(clock.instant());
        walletSignatureValidator.check(authMessage, signature);
        walletAuthMessageWriter.modifyAsUsed(authMessage);

        return authMessage.walletAddress();
    }
}
