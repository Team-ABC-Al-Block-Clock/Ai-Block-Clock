package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.enums.payment.ChargeStatusReason;
import com.aiblockclock.storage.payment.ChargeRequestRepository;
import com.aiblockclock.storage.payment.LedgerTransactionRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@PaymentMySqlTest
class ChargeRequestStateMySqlTest extends ChargeRequestStateConcurrencyIntegrationTest {

    @Autowired
    private ChargeRequestReader reader;
    @Autowired
    private ChargeRequestWriter writer;
    @Autowired
    private ChargeConfirmationManager confirmationManager;
    @Autowired
    private ChargeRequestRepository requests;
    @Autowired
    private LedgerTransactionRepository transactions;

    @Test
    void 확정과_실패가_동시에_저장되어도_하나만_상태를_변경한다() throws Exception {
        ChargeRequest saved = writer.saveNewRequest(ChargeRequest.pending(
                "state-race-user", "0x" + "1".repeat(40), 12345L,
                "0x" + "c".repeat(64),
                DepositAmount.of(new BigDecimal("1.000001"))));
        ChargeRequest confirmation = reader.read(saved.getId());
        ChargeRequest failure = reader.read(saved.getId());
        failure.fail(ChargeStatusReason.ONCHAIN_TRANSACTION_FAILED);
        CyclicBarrier startTogether = new CyclicBarrier(2);

        var executor = Executors.newFixedThreadPool(2);
        try {
            var confirmed = executor.submit(() -> attempt(startTogether,
                    () -> confirmationManager.confirm(
                            confirmation,
                            AbcAmount.of(new BigDecimal("1.000001"))
                    )));
            var failed = executor.submit(() -> attempt(startTogether, () -> writer.updateFromPending(failure)));
            assertThat(List.of(confirmed.get(30, TimeUnit.SECONDS), failed.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("saved", ErrorType.CHARGE_REQUEST_CONCURRENTLY_MODIFIED.name());
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        var persisted = requests.findById(saved.getId()).orElseThrow();
        assertThat(persisted.getVersion()).isEqualTo(1);
        assertThat(persisted.getStatus()).isIn(ChargeStatus.CONFIRMED, ChargeStatus.FAILED);
        assertThat(transactions.count()).isEqualTo(persisted.getStatus() == ChargeStatus.CONFIRMED ? 1 : 0);
    }

    private String attempt(CyclicBarrier startTogether, Callable<ChargeRequest> operation) throws Exception {
        startTogether.await(10, TimeUnit.SECONDS);
        try {
            operation.call();
            return "saved";
        } catch (CoreException exception) {
            return exception.getErrorType().name();
        }
    }
}
