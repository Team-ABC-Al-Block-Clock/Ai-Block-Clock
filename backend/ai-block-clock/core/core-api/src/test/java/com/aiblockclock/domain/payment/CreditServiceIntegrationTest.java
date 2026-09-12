package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.storage.payment.LedgerAccountEntity;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@Transactional
class CreditServiceIntegrationTest {

    @Autowired
    private CreditService creditService;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Test
    void 장부_계좌가_없으면_ABC_잔액은_0이다() {
        CreditBalance balance =
                creditService.readBalance("user-without-account");

        assertThat(balance.amount())
                .isEqualByComparingTo("0.000");
    }

    @Test
    void 사용자_식별값에_해당하는_ABC_잔액만_조회한다() {
        saveAccount("user-a", "12.000");
        saveAccount("user-b", "99.000");

        CreditBalance userABalance =
                creditService.readBalance("user-a");

        CreditBalance userBBalance =
                creditService.readBalance("user-b");

        assertThat(userABalance.amount())
                .isEqualByComparingTo("12.000");

        assertThat(userBBalance.amount())
                .isEqualByComparingTo("99.000");
    }

    private void saveAccount(
            String userReferenceId,
            String balance
    ) {
        LedgerAccountEntity account =
                LedgerAccountEntity.of(
                        LedgerAccountType.ABC_BALANCE,
                        userReferenceId
                );

        account.modifyBalance(new BigDecimal(balance));
        ledgerAccountRepository.save(account);
    }
}
