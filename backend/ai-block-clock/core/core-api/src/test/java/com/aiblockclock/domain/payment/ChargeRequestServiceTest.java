package com.aiblockclock.domain.payment;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChargeRequestServiceTest {

    private static final String USER_ID = "test-user";
    private static final String TRANSACTION_HASH = "test-hash";
    private static final ChargeNetworkPolicy NETWORK_POLICY = new ChargeNetworkPolicy(
            12345L, "test-custody", "test-token", 5L
    );

    private final ChargeRequestReader reader = mock(ChargeRequestReader.class);
    private final ChargeRequestWriter writer = mock(ChargeRequestWriter.class);
    private final ChargeRequestService service = new ChargeRequestService(reader, writer);

    @Test
    void 기존_요청이_없으면_새_요청을_저장한다() {
        ChargeRequest savedRequest = request(USER_ID);
        when(reader.findByChainAndTransactionHash(12345L, TRANSACTION_HASH)).thenReturn(Optional.empty());
        when(writer.saveNewRequest(any())).thenReturn(savedRequest);

        assertThat(create()).isSameAs(savedRequest);
        verify(reader).findByChainAndTransactionHash(12345L, TRANSACTION_HASH);
    }

    @Test
    void 같은_사용자의_기존_요청은_저장_없이_반환한다() {
        ChargeRequest existingRequest = request(USER_ID);
        when(reader.findByChainAndTransactionHash(12345L, TRANSACTION_HASH))
                .thenReturn(Optional.of(existingRequest));

        assertThat(create()).isSameAs(existingRequest);
        verify(writer, never()).saveNewRequest(any());
    }

    @Test
    void 다른_사용자의_기존_요청은_저장_없이_차단한다() {
        when(reader.findByChainAndTransactionHash(12345L, TRANSACTION_HASH))
                .thenReturn(Optional.of(request("other-user")));

        assertThatThrownBy(this::create).isInstanceOfSatisfying(CoreException.class,
                exception -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.CHARGE_TRANSACTION_ALREADY_USED));
        verify(writer, never()).saveNewRequest(any());
    }

    @Test
    void 중복_저장_후_기존_요청을_재조회하고_반환한다() {
        ChargeRequest existingRequest = request(USER_ID);
        when(reader.findByChainAndTransactionHash(12345L, TRANSACTION_HASH))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingRequest));
        when(writer.saveNewRequest(any())).thenThrow(new CoreException(ErrorType.CHARGE_REQUEST_ALREADY_EXISTS));

        assertThat(create()).isSameAs(existingRequest);

        var order = inOrder(reader, writer);
        order.verify(reader).findByChainAndTransactionHash(12345L, TRANSACTION_HASH);
        order.verify(writer).saveNewRequest(any());
        order.verify(reader).findByChainAndTransactionHash(12345L, TRANSACTION_HASH);
    }

    @Test
    void 중복_저장_후에도_다른_사용자의_거래는_차단한다() {
        when(reader.findByChainAndTransactionHash(12345L, TRANSACTION_HASH))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(request("other-user")));
        when(writer.saveNewRequest(any())).thenThrow(new CoreException(ErrorType.CHARGE_REQUEST_ALREADY_EXISTS));

        assertThatThrownBy(this::create).isInstanceOfSatisfying(CoreException.class,
                exception -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.CHARGE_TRANSACTION_ALREADY_USED));
    }

    @Test
    void 중복_이외의_오류는_재조회_없이_전달한다() {
        CoreException failure = new CoreException(ErrorType.CHARGE_INVALID_STATE);
        when(reader.findByChainAndTransactionHash(12345L, TRANSACTION_HASH)).thenReturn(Optional.empty());
        when(writer.saveNewRequest(any())).thenThrow(failure);

        assertThatThrownBy(this::create).isSameAs(failure);
        verify(reader).findByChainAndTransactionHash(12345L, TRANSACTION_HASH);
    }

    @Test
    void 중복_요청_재조회에_실패하면_중복_오류를_유지한다() {
        CoreException failure = new CoreException(ErrorType.CHARGE_REQUEST_ALREADY_EXISTS);
        when(reader.findByChainAndTransactionHash(12345L, TRANSACTION_HASH)).thenReturn(Optional.empty());
        when(writer.saveNewRequest(any())).thenThrow(failure);

        assertThatThrownBy(this::create).isSameAs(failure);
    }

    @Test
    void Service에는_트랜잭션_경계를_선언하지_않는다() {
        assertThat(AnnotatedElementUtils.hasAnnotation(ChargeRequestService.class, Transactional.class)).isFalse();
        Arrays.stream(ChargeRequestService.class.getDeclaredMethods()).forEach(method ->
                assertThat(AnnotatedElementUtils.hasAnnotation(method, Transactional.class)).isFalse());
    }

    private ChargeRequest create() {
        return service.createChargeRequest(USER_ID, "test-wallet", NETWORK_POLICY,
                TRANSACTION_HASH, new BigDecimal("1.000001"));
    }

    private ChargeRequest request(String userId) {
        return ChargeRequest.pending(userId, "test-wallet", 12345L, TRANSACTION_HASH, new BigDecimal("1.000001"));
    }
}
