package com.aiblockclock.domain.user;

import com.aiblockclock.domain.payment.LedgerAccountWriter;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.storage.payment.LedgerAccountEntity;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import com.aiblockclock.storage.user.UserRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@Tag("integration")
@SpringBootTest
class UserRegistrationManagerIntegrationTest {
    @Autowired
    private UserRegistrationManager userRegistrationManager;

    @Autowired
    private UserReader userReader;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private LedgerAccountWriter ledgerAccountWriter;

    private final String walletAddress = "0x" + UUID.randomUUID().toString().replace("-", "") + "00000000";
    private final List<UserId> createdUserIds = new ArrayList<>();

    @AfterEach
    void 테스트에서_생성한_사용자와_계정만_정리한다() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (UserId userId : createdUserIds) {
                ledgerAccountRepository.findByAccountTypeAndReferenceId(
                        LedgerAccountType.ABC_BALANCE, Long.toString(userId.value())
                ).ifPresent(ledgerAccountRepository::delete);
            }

            userRepository.findByWalletAddressIgnoreCase(walletAddress)
                    .ifPresent(userRepository::delete);
        });
    }

    @Test
    void 가입하면_사용자와_잔액이_0인_ABC_계정이_함께_생성된다() {
        String upperCaseAddress = "0x" + walletAddress.substring(2).toUpperCase(Locale.ROOT);

        User registered = userRegistrationManager.register(upperCaseAddress);
        createdUserIds.add(registered.id());

        User found = userReader.findByWalletAddress(walletAddress).orElseThrow();
        LedgerAccountEntity account = findAccount(registered.id());

        assertThat(found.id()).isEqualTo(registered.id());
        assertThat(found.walletAddress()).isEqualTo(walletAddress);
        assertThat(account.getId()).isPositive();
        assertThat(account.getAccountType()).isEqualTo(LedgerAccountType.ABC_BALANCE);
        assertThat(account.getReferenceId()).isEqualTo(Long.toString(registered.id().value()));
        assertThat(account.getBalance()).isEqualByComparingTo("0.000000");
        assertThat(account.getVersion()).isZero();
        assertThat(account.getCreatedAt()).isNotNull();
        assertThat(account.getUpdatedAt()).isNotNull();
    }

    @Test
    void ABC_계정_생성에_실패하면_사용자_저장도_취소된다() {
        doAnswer(invocation -> {
            UserId userId = invocation.getArgument(0);
            createdUserIds.add(userId);

            throw new CoreException(ErrorType.LEDGER_INVALID_TRANSACTION);
        }).when(ledgerAccountWriter).createAbcBalance(any(UserId.class));

        assertThatThrownBy(() -> userRegistrationManager.register(walletAddress))
                .isInstanceOf(CoreException.class);

        assertThat(userReader.findByWalletAddress(walletAddress)).isEmpty();
        assertAccountAbsent(createdUserIds.getFirst());
    }

    @Test
    void ABC_계정을_저장한_뒤_실패해도_사용자와_계정이_모두_취소된다() {
        doAnswer(invocation -> {
            UserId userId = invocation.getArgument(0);
            createdUserIds.add(userId);
            invocation.callRealMethod();

            throw new CoreException(ErrorType.LEDGER_INVALID_TRANSACTION);
        }).when(ledgerAccountWriter).createAbcBalance(any(UserId.class));

        assertThatThrownBy(() -> userRegistrationManager.register(walletAddress))
                .isInstanceOf(CoreException.class);

        assertThat(userReader.findByWalletAddress(walletAddress)).isEmpty();
        assertAccountAbsent(createdUserIds.getFirst());
    }

    @Test
    void 같은_지갑의_중복_가입_실패는_기존_사용자와_계정을_유지한다() {
        String upperCaseAddress = "0x" + walletAddress.substring(2).toUpperCase(Locale.ROOT);
        User registered = userRegistrationManager.register(walletAddress);
        createdUserIds.add(registered.id());
        Long accountId = findAccount(registered.id()).getId();

        assertThatThrownBy(() -> userRegistrationManager.register(upperCaseAddress))
                .isInstanceOf(DataIntegrityViolationException.class);

        User found = userReader.findByWalletAddress(walletAddress).orElseThrow();
        LedgerAccountEntity account = findAccount(found.id());

        assertThat(found.id()).isEqualTo(registered.id());
        assertThat(account.getId()).isEqualTo(accountId);
        assertThat(account.getBalance()).isEqualByComparingTo("0.000000");
    }

    private LedgerAccountEntity findAccount(UserId userId) {
        return ledgerAccountRepository.findByAccountTypeAndReferenceId(
                LedgerAccountType.ABC_BALANCE, Long.toString(userId.value())
        ).orElseThrow();
    }

    private void assertAccountAbsent(UserId userId) {
        assertThat(ledgerAccountRepository.findByAccountTypeAndReferenceId(
                LedgerAccountType.ABC_BALANCE, Long.toString(userId.value())
        )).isEmpty();
    }
}
