package com.aiblockclock.api.batch;

import com.aiblockclock.domain.execution.Execution;
import com.aiblockclock.domain.execution.ExecutionService;
import com.aiblockclock.domain.execution.UnfinishedExecution;
import com.aiblockclock.enums.execution.ExecutionStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 감시 목록은 메모리에 있어, 서버를 다시 띄우면 도는 실행을 아무도 감시하지 않는다. 끝나지 않은 실행을 DB 에서 찾아 다시 붙인다.
// 이벤트는 큐에 남아 있다가 다시 오므로 복구는 감시(기한)만 다시 건다.
//   - 넘긴 채 RUNNING 인 실행: 감시에 다시 붙인다. 기한은 붙인 시각부터 잰다. 이미 감시 중이면 그대로 둔다
//   - 넘긴 채 QUEUED 인 실행: 큐에서 기다리는 중이다. 보통은 TTL 과 dead letter 가 닫지만, dead letter 가 버려지면 아무도
//     닫지 않는다. 큐 대기 안전망에 넘겨 넘긴 지 큐 대기 한도 + 유예(execution.recovery.queue-grace)가 지났으면 닫게 한다
//   - 넘기지 못한 실행: 디스패처가 다시 발행하거나 한도가 지나면 버린다. 복구가 끼면 둘이 엉킨다
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "execution.recovery", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ExecutionRecoveryScheduler {
    private final ExecutionService executionService;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        recover();
    }

    // 한 건이 실패해도 나머지는 본다. 실패한 건은 DB 에 끝나지 않은 채 남아 다음 차례에 다시 본다.
    @Scheduled(fixedDelayString = "${execution.recovery.interval}", initialDelayString = "${execution.recovery.interval}")
    public void recover() {
        for (UnfinishedExecution unfinished : executionService.readUnfinished()) {
            Execution execution = unfinished.execution();

            if (!execution.isDispatched()) {
                continue;
            }

            try {
                recoverDispatched(execution);
            } catch (RuntimeException exception) {
                log.error("넘긴 실행을 다시 붙잡지 못했다. 다음 차례에 다시 본다: execution={} status={}",
                        execution.getId().value(), execution.getStatus(), exception);
            }
        }
    }

    // 끝나지 않은 실행은 RUNNING 아니면 QUEUED 다.
    private void recoverDispatched(Execution dispatched) {
        if (dispatched.getStatus() == ExecutionStatus.RUNNING) {
            executionService.beginWatching(dispatched);

            return;
        }

        executionService.closeOverdueInQueue(dispatched.getId());
    }
}
