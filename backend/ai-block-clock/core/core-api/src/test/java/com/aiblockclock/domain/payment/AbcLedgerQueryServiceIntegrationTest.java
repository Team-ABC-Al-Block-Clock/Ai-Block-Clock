package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.enums.payment.LedgerEventType;
import com.aiblockclock.storage.payment.LedgerAccountEntity;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import com.aiblockclock.storage.payment.LedgerEntryEntity;
import com.aiblockclock.storage.payment.LedgerEntryRepository;
import com.aiblockclock.storage.payment.LedgerTransactionEntity;
import com.aiblockclock.storage.payment.LedgerTransactionRepository;
import com.aiblockclock.support.paging.CursorPage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@Transactional
class AbcLedgerQueryServiceIntegrationTest {

    @Autowired
    private AbcLedgerQueryService abcLedgerQueryService;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Test
    void 장부_계좌가_없으면_ABC_잔액과_예약_금액은_0이다() {
        AbcBalance balance =
                abcLedgerQueryService.readBalance(UserId.of(999L));

        assertThat(balance.available()).isEqualByComparingTo("0");
        assertThat(balance.reserved()).isEqualByComparingTo("0");
    }

    @Test
    void 사용자_식별값에_해당하는_ABC_잔액만_조회한다() {
        saveAccount("1", "12.000");
        saveAccount("2", "99.000");

        AbcBalance userABalance =
                abcLedgerQueryService.readBalance(UserId.of(1L));

        AbcBalance userBBalance =
                abcLedgerQueryService.readBalance(UserId.of(2L));

        assertThat(userABalance.available())
                .isEqualByComparingTo("12.000");

        assertThat(userBBalance.available())
                .isEqualByComparingTo("99.000");
    }

    @Test
    void 실행에_묶인_ABC는_예약_금액으로_따로_조회한다() {
        saveAccount("1", "70.000");
        LedgerAccountEntity escrowAccount =
                saveAccount(LedgerAccountType.EXECUTION_ESCROW, "1");
        ledgerAccountRepository.addBalanceDelta(escrowAccount.getId(), new BigDecimal("30.000"), Instant.now());

        AbcBalance balance = abcLedgerQueryService.readBalance(UserId.of(1L));

        assertThat(balance.available()).isEqualByComparingTo("70.000");
        assertThat(balance.reserved()).isEqualByComparingTo("30.000");
    }

    @Test
    void 장부_계좌가_없으면_ABC_사용_내역은_비어_있다() {
        CursorPage<AbcTransaction> transactionPage =
                abcLedgerQueryService.readTransactions(
                        UserId.of(999L),
                        AbcTransactionSearchCondition.allDates(null),
                        null,
                        10
                );

        assertThat(transactionPage.content()).isEmpty();
        assertThat(transactionPage.hasNext()).isFalse();
        assertThat(transactionPage.nextCursor()).isNull();
    }

    @Test
    void 사용자_ABC_사용_내역만_최신순으로_조회한다() {
        LedgerAccountEntity issuanceAccount =
                saveAccount(
                        LedgerAccountType.ABC_ISSUANCE,
                        "SYSTEM"
                );

        LedgerAccountEntity userAAccount =
                saveAccount("1", "0.000");

        LedgerAccountEntity userBAccount =
                saveAccount("2", "0.000");

        LedgerAccountEntity escrowAccount =
                saveAccount(
                        LedgerAccountType.EXECUTION_ESCROW,
                        "execution-a"
                );

        LedgerTransactionEntity userACharge =
                recordTransfer(
                        LedgerEventType.CHARGE,
                        "charge-user-a",
                        "CHARGE_REQUEST",
                        "101",
                        "0xabc123",
                        issuanceAccount,
                        userAAccount,
                        "10.000"
                );

        recordTransfer(
                LedgerEventType.CHARGE,
                "charge-user-b",
                "CHARGE_REQUEST",
                "102",
                issuanceAccount,
                userBAccount,
                "20.000"
        );

        LedgerTransactionEntity userAReserve =
                recordTransfer(
                        LedgerEventType.RESERVE,
                        "reserve-user-a",
                        "EXECUTION",
                        "201",
                        userAAccount,
                        escrowAccount,
                        "3.000"
                );

        List<AbcTransaction> transactions =
                abcLedgerQueryService.readTransactions(
                                UserId.of(1L),
                                AbcTransactionSearchCondition.allDates(null),
                                null,
                                10
                        )
                        .content();

        assertThat(transactions)
                .extracting(AbcTransaction::transactionId)
                .containsExactly(
                        userAReserve.getId(),
                        userACharge.getId()
                );

        assertThat(transactions)
                .extracting(AbcTransaction::eventType)
                .containsExactly(
                        LedgerEventType.RESERVE,
                        LedgerEventType.CHARGE
                );

        assertThat(transactions.get(0).amount())
                .isEqualByComparingTo("-3.000");

        assertThat(transactions.get(1).amount())
                .isEqualByComparingTo("10.000");

        AbcTransaction reserveLine = transactions.get(0);
        assertThat(reserveLine.referenceType()).isEqualTo("EXECUTION");
        assertThat(reserveLine.referenceId()).isEqualTo("201");
        assertThat(reserveLine.transactionHash()).isNull();

        AbcTransaction chargeLine = transactions.get(1);
        assertThat(chargeLine.referenceType()).isEqualTo("CHARGE_REQUEST");
        assertThat(chargeLine.referenceId()).isEqualTo("101");
        assertThat(chargeLine.transactionHash()).isEqualTo("0xabc123");
    }

    private LedgerAccountEntity saveAccount(
            String userReferenceId,
            String balance
    ) {
        LedgerAccountEntity account =
                saveAccount(
                        LedgerAccountType.ABC_BALANCE,
                        userReferenceId
                );

        // 잔액은 운영 코드와 같은 경로(증감 UPDATE)로만 바꾼다. 쿼리가 영속성 컨텍스트를 비우므로 다시 읽는다.
        ledgerAccountRepository.addBalanceDelta(account.getId(), new BigDecimal(balance), Instant.now());

        return ledgerAccountRepository.findById(account.getId()).orElseThrow();
    }

    private LedgerAccountEntity saveAccount(
            LedgerAccountType accountType,
            String referenceId
    ) {
        LedgerAccountEntity account =
                LedgerAccountEntity.of(
                        accountType,
                        referenceId
                );

        return ledgerAccountRepository.save(account);
    }

    private LedgerTransactionEntity recordTransfer(
            LedgerEventType eventType,
            String idempotencyKey,
            String referenceType,
            String referenceId,
            LedgerAccountEntity fromAccount,
            LedgerAccountEntity toAccount,
            String amount
    ) {
        return recordTransfer(eventType, idempotencyKey, referenceType, referenceId, null,
                fromAccount, toAccount, amount);
    }

    // 온체인 거래가 있는 충전 줄을 만들 때 해시까지 넣는다.
    private LedgerTransactionEntity recordTransfer(
            LedgerEventType eventType,
            String idempotencyKey,
            String referenceType,
            String referenceId,
            String transactionHash,
            LedgerAccountEntity fromAccount,
            LedgerAccountEntity toAccount,
            String amount
    ) {
        LedgerTransactionEntity transaction =
                ledgerTransactionRepository.save(
                        LedgerTransactionEntity.of(
                                eventType,
                                idempotencyKey,
                                referenceType,
                                referenceId,
                                null,
                                transactionHash
                        )
                );

        BigDecimal transferAmount = new BigDecimal(amount);

        ledgerEntryRepository.saveAllAndFlush(
                List.of(
                        LedgerEntryEntity.of(
                                transaction,
                                fromAccount,
                                transferAmount.negate()
                        ),
                        LedgerEntryEntity.of(
                                transaction,
                                toAccount,
                                transferAmount
                        )
                )
        );

        return transaction;
    }
}
