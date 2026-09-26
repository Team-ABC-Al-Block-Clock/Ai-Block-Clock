package com.aiblockclock.api.batch;

import com.aiblockclock.domain.payment.ChargeProcessingService;
import com.aiblockclock.domain.payment.ChargeProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

// 확인 중(PENDING)인 충전을 주기적으로 다시 확인한다. 대기 목록을 DB 에서 읽으므로 서버를 다시 띄워도 이어진다.
// 체인에 닿지 못하면 남은 요청을 이번 차례에 더 묻지 않고, 다음 차례까지의 간격을 두 배씩 늘린다.
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "payment.charge", name = "polling-enabled", havingValue = "true")
public class ChargeVerificationScheduler {
    private final TaskScheduler chargeScheduler;
    private final ChargeProcessingService chargeProcessingService;
    private final ChargeProperties chargeProperties;
    private final Clock clock;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        scheduleAfter(chargeProperties.pollInterval());
    }

    private void runOnce(Duration currentDelay) {
        boolean chainReached = true;

        try {
            chainReached = recheckPendingCharges();
        } catch (RuntimeException exception) {
            log.error("대기 중인 충전 목록을 읽지 못했다", exception);
        } finally {
            scheduleAfter(nextDelay(currentDelay, chainReached));
        }
    }

    private boolean recheckPendingCharges() {
        for (Long chargeRequestId : chargeProcessingService.readPendingChargeIds()) {
            try {
                if (!chargeProcessingService.process(chargeRequestId).chainReached()) {
                    return false;
                }
            } catch (RuntimeException exception) {
                // 한 건이 깨져도 나머지는 확인한다. 깨진 건은 다음 차례에 다시 본다
                log.warn("충전 재확인에 실패했다. chargeRequestId={}", chargeRequestId, exception);
            }
        }

        return true;
    }

    private Duration nextDelay(Duration currentDelay, boolean chainReached) {
        if (chainReached) {
            return chargeProperties.pollInterval();
        }

        Duration doubled = currentDelay.multipliedBy(2);
        Duration next = doubled.compareTo(chargeProperties.maxPollInterval()) > 0
                ? chargeProperties.maxPollInterval()
                : doubled;

        log.warn("SSAFY 체인에 닿지 못해 충전 재확인을 {}초 뒤로 미룬다", next.toSeconds());

        return next;
    }

    private void scheduleAfter(Duration delay) {
        chargeScheduler.schedule(() -> runOnce(delay), clock.instant().plus(delay));
    }
}
