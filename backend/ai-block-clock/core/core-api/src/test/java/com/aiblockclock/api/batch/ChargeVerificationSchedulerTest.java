package com.aiblockclock.api.batch;

import com.aiblockclock.domain.payment.ChargeProcessResult;
import com.aiblockclock.domain.payment.ChargeProcessingService;
import com.aiblockclock.domain.payment.ChargeProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ChargeVerificationSchedulerTest {
    private static final Instant NOW = Instant.parse("2026-09-25T00:00:00Z");
    private static final ChargeProperties PROPERTIES = new ChargeProperties(
            12345L, "0x2222222222222222222222222222222222222222", "0x3333333333333333333333333333333333333333", 1L,
            Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofMinutes(30), true);

    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);
    private final ChargeProcessingService service = mock(ChargeProcessingService.class);
    private final ChargeVerificationScheduler scheduler = new ChargeVerificationScheduler(
            taskScheduler, service, PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void 서버가_뜨면_기본_간격_뒤에_첫_확인을_잡는다() {
        scheduler.start();

        verify(taskScheduler).schedule(any(Runnable.class), eq(NOW.plusSeconds(10)));
    }

    @Test
    void 체인에_닿으면_대기_중인_충전을_모두_확인하고_기본_간격으로_다시_잡는다() {
        given(service.readPendingChargeIds()).willReturn(List.of(1L, 2L));
        given(service.process(any())).willReturn(new ChargeProcessResult(null, true));

        List<Instant> planned = runTimes(2);

        verify(service).process(1L);
        verify(service).process(2L);
        assertThat(planned).containsExactly(NOW.plusSeconds(10), NOW.plusSeconds(10));
    }

    @Test
    void 체인에_닿지_못하면_남은_충전은_건너뛰고_간격을_두_배씩_늘리되_상한에서_멈춘다() {
        given(service.readPendingChargeIds()).willReturn(List.of(1L, 2L));
        given(service.process(1L)).willReturn(new ChargeProcessResult(null, false));

        List<Instant> planned = runTimes(4);

        verify(service, never()).process(2L);
        assertThat(planned).containsExactly(
                NOW.plusSeconds(10), NOW.plusSeconds(20), NOW.plusSeconds(30), NOW.plusSeconds(30));
    }

    @Test
    void 한_건이_깨져도_나머지를_확인하고_다음_차례를_잡는다() {
        given(service.readPendingChargeIds()).willReturn(List.of(1L, 2L));
        given(service.process(1L)).willThrow(new IllegalStateException("broken"));
        given(service.process(2L)).willReturn(new ChargeProcessResult(null, true));

        List<Instant> planned = runTimes(2);

        verify(service).process(2L);
        assertThat(planned).last().isEqualTo(NOW.plusSeconds(10));
    }

    @Test
    void 대기_목록을_읽지_못해도_다음_차례는_잡는다() {
        given(service.readPendingChargeIds()).willThrow(new IllegalStateException("db down"));

        assertThat(runTimes(2)).last().isEqualTo(NOW.plusSeconds(10));
    }

    // 시작을 포함해 차례를 count 번 잡을 때까지 잡힌 일을 직접 돌리고, 잡힌 시각들을 돌려준다
    private List<Instant> runTimes(int count) {
        scheduler.start();

        for (int round = 1; round < count; round++) {
            ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            verify(taskScheduler, times(round)).schedule(task.capture(), any(Instant.class));
            task.getValue().run();
        }

        ArgumentCaptor<Instant> plannedAt = ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler, times(count)).schedule(any(Runnable.class), plannedAt.capture());

        return plannedAt.getAllValues();
    }
}
