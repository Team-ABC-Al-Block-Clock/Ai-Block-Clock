package com.aiblockclock.domain.payment;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import com.aiblockclock.domain.user.User;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.user.UserReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChargeRequestServiceTest {

    private static final UserId REQUESTER = UserId.of(7L);
    private static final String USER_ID = "7";
    private static final String WALLET = "0x1111111111111111111111111111111111111111";
    private static final String TRANSACTION_HASH = "test-hash";
    private static final String CUSTODY = "0x2222222222222222222222222222222222222222";
    private static final ChargeProperties CHARGE_PROPERTIES = new ChargeProperties(
            12345L, CUSTODY, "0x3333333333333333333333333333333333333333", 5L,
            Duration.ofSeconds(10), Duration.ofMinutes(5), Duration.ofMinutes(30), false
    );

    private final ChargeRequestReader reader = mock(ChargeRequestReader.class);
    private final ChargeRequestWriter writer = mock(ChargeRequestWriter.class);
    private final UserReader userReader = mock(UserReader.class);
    private final ChargeRequestService service =
            new ChargeRequestService(reader, writer, userReader, CHARGE_PROPERTIES);

    @BeforeEach
    void 로그인한_사용자의_지갑을_준비한다() {
        when(userReader.read(REQUESTER)).thenReturn(userWith(WALLET));
    }

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
    void 금액이_null이면_잘못된_충전_수량_오류로_변환한다() {
        assertInvalidAmount(null);
        verifyNoInteractions(reader, writer);
    }

    @Test
    void 소수_7자리_금액은_잘못된_충전_수량_오류로_변환한다() {
        assertInvalidAmount(new BigDecimal("1.0000001"));
        verifyNoInteractions(reader, writer);
    }

    @Test
    void 소수_6자리_이하_금액은_6자리로_정규화한다() {
        when(reader.findByChainAndTransactionHash(12345L, TRANSACTION_HASH))
                .thenReturn(Optional.empty());
        when(writer.saveNewRequest(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChargeRequest createdRequest = create(new BigDecimal("1.1"));

        assertThat(createdRequest.getRequestedDepositAmount().value())
                .isEqualByComparingTo("1.100000");
        assertThat(createdRequest.getRequestedDepositAmount().value().scale())
                .isEqualTo(6);
    }

    @Test
    void 보낸_지갑은_요청이_아니라_로그인한_사용자의_지갑이다() {
        when(reader.findByChainAndTransactionHash(12345L, TRANSACTION_HASH)).thenReturn(Optional.empty());
        when(writer.saveNewRequest(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ChargeRequest createdRequest = create();

        assertThat(createdRequest.getSenderWalletAddress()).isEqualTo(WALLET);
        assertThat(createdRequest.getUserReferenceId()).isEqualTo(USER_ID);
        assertThat(createdRequest.getChainId()).isEqualTo(12345L);
    }

    @Test
    void 로그인한_지갑이_보관_지갑이면_저장하지_않고_막는다() {
        when(userReader.read(REQUESTER)).thenReturn(userWith(CUSTODY.toUpperCase().replace("0X", "0x")));

        assertThatThrownBy(this::create).isInstanceOfSatisfying(CoreException.class,
                exception -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.CHARGE_SENDER_IS_CUSTODY));
        verifyNoInteractions(reader, writer);
    }

    @Test
    void 남의_충전_요청은_조회할_수_없다() {
        when(reader.read(1L)).thenReturn(request("8"));

        assertThatThrownBy(() -> service.getOwnedCharge(1L, REQUESTER)).isInstanceOfSatisfying(CoreException.class,
                exception -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.CHARGE_REQUEST_FORBIDDEN));
    }

    @Test
    void Service에는_트랜잭션_경계를_선언하지_않는다() {
        assertThat(AnnotatedElementUtils.hasAnnotation(ChargeRequestService.class, Transactional.class)).isFalse();
        Arrays.stream(ChargeRequestService.class.getDeclaredMethods()).forEach(method ->
                assertThat(AnnotatedElementUtils.hasAnnotation(method, Transactional.class)).isFalse());
    }

    private ChargeRequest create() {
        return create(new BigDecimal("1.000001"));
    }

    private ChargeRequest create(BigDecimal requestedDepositAmount) {
        return service.createChargeRequest(
                REQUESTER,
                TRANSACTION_HASH,
                requestedDepositAmount
        );
    }

    private void assertInvalidAmount(BigDecimal requestedDepositAmount) {
        assertThatThrownBy(() -> create(requestedDepositAmount))
                .isInstanceOfSatisfying(
                        CoreException.class,
                        exception -> assertThat(exception.getErrorType())
                                .isEqualTo(ErrorType.CHARGE_INVALID_AMOUNT)
                );
    }

    private ChargeRequest request(String userId) {
        return ChargeRequest.pending(
                userId,
                "test-wallet",
                12345L,
                TRANSACTION_HASH,
                DepositAmount.of(new BigDecimal("1.000001"))
        );
    }

    private static User userWith(String walletAddress) {
        return new User(REQUESTER, walletAddress, null, null, null, 0L, Instant.EPOCH, Instant.EPOCH);
    }
}
