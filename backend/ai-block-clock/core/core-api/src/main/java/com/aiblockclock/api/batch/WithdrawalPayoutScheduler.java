package com.aiblockclock.api.batch;

import com.aiblockclock.domain.payment.WithdrawalPayoutProperties;
import com.aiblockclock.domain.payment.WithdrawalPayoutResult;
import com.aiblockclock.domain.payment.WithdrawalPayoutService;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Clock;

// 승인된 출금을 주기적으로 한 건씩 보낸다. 대기 목록을 DB 에서 읽으므로 서버를 다시 띄워도 이어진다.
// 지급을 켠 서버에서만 뜬다. 켰는데 서명할 지갑이 보관 지갑이 아니면 기동을 멈춘다 — 조용히 못 보내는 것보다 낫다.
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "payment.withdrawal.payout", name = "enabled", havingValue = "true")
public class WithdrawalPayoutScheduler {
    private final TaskScheduler payoutScheduler;
    private final WithdrawalPayoutService withdrawalPayoutService;
    private final WithdrawalPayoutProperties withdrawalPayoutProperties;
    private final Clock clock;

    @PostConstruct
    void ensureReady() {
        withdrawalPayoutService.ensureReady();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        scheduleNext();
    }

    private void runOnce() {
        try {
            report(withdrawalPayoutService.payNext());
        } catch (RuntimeException exception) {
            // 한 차례가 깨져도 다음 차례는 돈다. 깨진 출금은 상태가 그대로라 다음 차례에 다시 본다
            log.error("출금 지급 차례가 실패했다", exception);
        } finally {
            scheduleNext();
        }
    }

    private void report(WithdrawalPayoutResult result) {
        switch (result.outcome()) {
            case IDLE, WAITING -> {
            }
            case SUBMITTED -> log.info("출금을 체인에 보냈다. withdrawalId={}", result.withdrawalId());
            case PAID -> log.info("출금 지급을 마쳤다. withdrawalId={}", result.withdrawalId());
            case CUSTODY_BALANCE_SHORT -> log.warn("보관 지갑의 abcToken 이 모자라 출금을 보내지 못했다. withdrawalId={}",
                    result.withdrawalId());
            case FAILED -> log.warn("체인이 출금 거래를 되돌려 실패로 닫고 금액을 정산금으로 돌려놓았다. withdrawalId={}",
                    result.withdrawalId());
            case HELD -> log.error("보낸 출금의 결과를 오래 알 수 없어 보류로 넘겼다. 금액은 묶어 두고 뒤의 출금도 기다린다. withdrawalId={}",
                    result.withdrawalId());
            case UNREACHABLE -> log.warn("SSAFY 체인에 닿지 못해 출금 지급을 다음 차례로 미룬다. withdrawalId={}",
                    result.withdrawalId());
        }
    }

    private void scheduleNext() {
        payoutScheduler.schedule(this::runOnce,
                clock.instant().plus(withdrawalPayoutProperties.pollInterval()));
    }
}
