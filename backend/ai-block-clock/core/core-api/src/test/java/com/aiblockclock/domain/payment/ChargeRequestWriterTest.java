package com.aiblockclock.domain.payment;

import com.aiblockclock.storage.payment.ChargeRequestRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class ChargeRequestWriterTest {

    private final ChargeRequestRepository repository = mock(ChargeRequestRepository.class);
    private final ChargeRequestReader reader = mock(ChargeRequestReader.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final ChargeRequestWriter writer = new ChargeRequestWriter(repository, reader, transactionManager);

    @Test
    void 삽입_롤백_후_독립된_읽기_트랜잭션에서_기존_요청을_반환한다() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("duplicate",
                new ConstraintViolationException("duplicate", new SQLException(), "uq_charge_request_chain_hash"));
        TransactionStatus insertStatus = mock(TransactionStatus.class);
        TransactionStatus readStatus = mock(TransactionStatus.class);
        ChargeRequest existingRequest = request();
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(insertStatus, readStatus);
        when(repository.saveAndFlush(any())).thenThrow(failure);
        when(reader.findByChainAndTransactionHash(12345L, "test-hash"))
                .thenReturn(Optional.of(existingRequest));

        assertThat(writer.appendOrGetExisting(request())).isSameAs(existingRequest);

        InOrder recoveryOrder = inOrder(transactionManager, reader);
        recoveryOrder.verify(transactionManager).rollback(insertStatus);
        recoveryOrder.verify(reader).findByChainAndTransactionHash(12345L, "test-hash");
        recoveryOrder.verify(transactionManager).commit(readStatus);
        ArgumentCaptor<TransactionDefinition> definitions = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager, times(2)).getTransaction(definitions.capture());
        assertThat(definitions.getAllValues()).allSatisfy(definition -> assertThat(definition.getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        assertThat(definitions.getAllValues().get(0).isReadOnly()).isFalse();
        assertThat(definitions.getAllValues().get(1).isReadOnly()).isTrue();
    }

    @Test
    void 다른_무결성_오류는_중복_요청으로_숨기지_않는다() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("other constraint",
                new ConstraintViolationException("other constraint", new SQLException(), "other_constraint"));
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(mock(TransactionStatus.class));
        when(repository.saveAndFlush(any())).thenThrow(failure);

        assertThatThrownBy(() -> writer.appendOrGetExisting(request())).isSameAs(failure);
        verify(reader, never()).findByChainAndTransactionHash(any(), any());
    }

    @Test
    void 중복_오류여도_기존_요청을_찾지_못하면_원래_오류를_유지한다() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("duplicate",
                new ConstraintViolationException("duplicate", new SQLException(), "uq_charge_request_chain_hash"));
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(mock(TransactionStatus.class));
        when(repository.saveAndFlush(any())).thenThrow(failure);
        when(reader.findByChainAndTransactionHash(12345L, "test-hash")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> writer.appendOrGetExisting(request())).isSameAs(failure);
    }

    private ChargeRequest request() {
        return ChargeRequest.pending("test-user", "test-wallet", 12345L, "test-hash", new BigDecimal("1.000001"));
    }
}
