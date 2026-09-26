package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.enums.payment.ChargeStatusReason;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ChargeProcessingServiceTest {
    private static final String HASH = "0x" + "a".repeat(64);
    private static final String SENDER = "0x1111111111111111111111111111111111111111";
    private static final ChargeProperties PROPERTIES = new ChargeProperties(
            ChargeChainFixtures.CHAIN_ID, ChargeChainFixtures.CUSTODY, ChargeChainFixtures.TOKEN, 5L,
            Duration.ofSeconds(10), Duration.ofMinutes(5), Duration.ofMinutes(30), false);

    private final ChargeRequestReader reader = mock(ChargeRequestReader.class);
    private final OnChainTransferReader transferReader = mock(OnChainTransferReader.class);
    private final ChargeRequestWriter writer = mock(ChargeRequestWriter.class);
    private final ChargeConfirmationManager confirmationManager = mock(ChargeConfirmationManager.class);
    private final ChargeProcessingService service = new ChargeProcessingService(reader, transferReader,
            new OnChainChargeVerifier(), new AbcConversionPolicy(), writer, confirmationManager, PROPERTIES);

    @Test
    void 대기_한도가_지나도록_블록에_적히지_않으면_시간_초과로_닫는다() {
        given(reader.read(1L)).willReturn(pendingRequestedAt(Instant.now().minus(Duration.ofMinutes(31))));
        given(transferReader.read(any())).willReturn(waiting());
        given(writer.updateFromPending(any())).willAnswer(invocation -> invocation.getArgument(0));

        ChargeRequest result = service.process(1L).chargeRequest();

        assertThat(result.getStatus()).isEqualTo(ChargeStatus.FAILED);
        assertThat(result.getStatusReason()).isEqualTo(ChargeStatusReason.VERIFICATION_TIMEOUT);
    }

    @Test
    void 대기_한도_안이면_확인_중으로_둔다() {
        given(reader.read(1L)).willReturn(pendingRequestedAt(Instant.now().minus(Duration.ofMinutes(29))));
        given(transferReader.read(any())).willReturn(waiting());

        ChargeProcessResult result = service.process(1L);

        assertThat(result.chainReached()).isTrue();
        assertThat(result.chargeRequest().getStatus()).isEqualTo(ChargeStatus.PENDING);
        verify(writer, never()).updateFromPending(any());
    }

    @Test
    void 체인에_닿지_못한_동안은_대기_한도가_지나도_닫지_않는다() {
        given(reader.read(1L)).willReturn(pendingRequestedAt(Instant.now().minus(Duration.ofHours(3))));
        given(transferReader.read(any())).willReturn(OnChainTokenTransfer.unknown(HASH));

        ChargeProcessResult result = service.process(1L);

        assertThat(result.chainReached()).isFalse();
        assertThat(result.chargeRequest().getStatus()).isEqualTo(ChargeStatus.PENDING);
        verify(writer, never()).updateFromPending(any());
    }

    @Test
    void 이미_닫힌_요청은_체인에_다시_묻지_않는다() {
        ChargeRequest closed = pendingRequestedAt(Instant.now());
        closed.fail(ChargeStatusReason.AMOUNT_MISMATCH);
        given(reader.read(1L)).willReturn(closed);

        assertThat(service.process(1L).chargeRequest()).isSameAs(closed);
        verify(transferReader, never()).read(any());
    }

    @Test
    void 다른_확인이_먼저_옮겼으면_그_결과를_다시_읽어_돌려준다() {
        ChargeRequest confirmedByOther = pendingRequestedAt(Instant.now());
        given(reader.read(1L)).willReturn(pendingRequestedAt(Instant.now()), confirmedByOther);
        given(transferReader.read(any())).willReturn(deposited());
        given(confirmationManager.confirm(any(), any()))
                .willThrow(new CoreException(ErrorType.CHARGE_REQUEST_CONCURRENTLY_MODIFIED));

        assertThat(service.process(1L).chargeRequest()).isSameAs(confirmedByOther);
    }

    private static ChargeRequest pendingRequestedAt(Instant requestedAt) {
        return ChargeRequest.restore(1L, "7", SENDER, ChargeChainFixtures.CHAIN_ID, HASH,
                DepositAmount.of(BigDecimal.TEN), requestedAt, null, ChargeStatus.PENDING, null, null, 0L);
    }

    private static OnChainTokenTransfer waiting() {
        return new OnChainTokenTransfer(OnChainTransactionStatus.NOT_FOUND, null, HASH, null, null, null, null, 0L);
    }

    private static OnChainTokenTransfer deposited() {
        return new OnChainTokenTransfer(OnChainTransactionStatus.SUCCESS, ChargeChainFixtures.CHAIN_ID, HASH, SENDER,
                ChargeChainFixtures.CUSTODY, ChargeChainFixtures.TOKEN, BigDecimal.TEN, 5L);
    }
}
