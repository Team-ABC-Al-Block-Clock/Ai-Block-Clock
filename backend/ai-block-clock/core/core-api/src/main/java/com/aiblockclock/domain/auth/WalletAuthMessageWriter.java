package com.aiblockclock.domain.auth;

import com.aiblockclock.storage.auth.WalletAuthMessageEntity;
import com.aiblockclock.storage.auth.WalletAuthMessageRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class WalletAuthMessageWriter {

    // V5의 지갑 주소 UNIQUE 제약과 연결되며, Writer 테스트가 실제 제약 이름을 확인한다.
    private static final String WALLET_ADDRESS_UNIQUE_CONSTRAINT = "uq_wallet_auth_message_wallet_address";

    private final WalletAuthMessageRepository walletAuthMessageRepository;

    @Transactional
    public WalletAuthMessage append(WalletAuthMessage issuedMessage) {
        String normalizedWalletAddress = issuedMessage.walletAddress().toLowerCase(Locale.ROOT);

        try {
            Optional<WalletAuthMessageEntity> existingMessage = walletAuthMessageRepository
                    .findByWalletAddress(normalizedWalletAddress);
            WalletAuthMessageEntity entity;

            if (existingMessage.isPresent()) {
                entity = existingMessage.get();
                entity.reissue(issuedMessage.nonce(), issuedMessage.message(),
                        issuedMessage.issuedAt(), issuedMessage.expiresAt());
            } else {
                entity = WalletAuthMessageEntity.of(normalizedWalletAddress, issuedMessage.nonce(),
                        issuedMessage.message(), issuedMessage.issuedAt(), issuedMessage.expiresAt());
            }

            WalletAuthMessageEntity saved = walletAuthMessageRepository.saveAndFlush(entity);

            return WalletAuthMessageEntityConverter.toDomain(saved);
        } catch (OptimisticLockingFailureException exception) {
            throw new CoreException(ErrorType.WALLET_AUTH_MESSAGE_CONCURRENTLY_MODIFIED);
        } catch (DataIntegrityViolationException exception) {
            if (!isDuplicateWalletAddress(exception)) {
                throw exception;
            }

            throw new CoreException(ErrorType.WALLET_AUTH_MESSAGE_CONCURRENTLY_MODIFIED);
        }
    }

    private boolean isDuplicateWalletAddress(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String constraintName = violation.getConstraintName();

                return constraintName != null && constraintName.toLowerCase(Locale.ROOT)
                        .contains(WALLET_ADDRESS_UNIQUE_CONSTRAINT);
            }
        }

        return false;
    }
}
