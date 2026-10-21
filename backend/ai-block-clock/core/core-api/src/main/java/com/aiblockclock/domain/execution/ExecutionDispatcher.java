package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunQueuePublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// 접수한 실행을 브로커(abc.runs)로 넘긴다. 사용자 요청은 DB(실행 · 선차감 · outbox)에만 쓰고, 넘기는 일은 여기서 한다.
//   onAccepted      접수 트랜잭션이 커밋된 뒤 그 실행의 발행을 곧바로 시도한다. 요청 스레드는 기다리지 않는다
//   dispatch        한 실행을 발행하고 confirm 을 받으면 runId(= 실행 번호)와 넘긴 시각을 남긴다. 실패해도 던지지 않는다
//   dispatchPending 주기마다 넘기지 않은 실행을 모두 발행해 본다. abandon-after 가 지나도록 못 넘긴 실행은 버린다
// 커밋 직후 시도와 주기 점검이 겹쳐 같은 실행이 두 번 나가도 된다. 런타임이 실행 번호로 두 번째를 거르고,
// 넘긴 기록은 행을 잠근 뒤 한 번만 적힌다.
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(ExecutionDispatchProperties.class)
public class ExecutionDispatcher implements DisposableBean {
    // 발행은 confirm 을 기다리느라 브로커가 느리면 오래 걸린다. 몇 개로 나눠 돌리고, 밀린 것은 주기 점검이 메운다.
    private static final int THREADS = 2;
    private static final int QUEUE_CAPACITY = 1_000;

    private final ExecutionReader executionReader;
    private final ExecutionReservationManager executionReservationManager;
    private final RunQueuePublisher runQueuePublisher;
    private final ExecutionDispatchProperties properties;
    private final Clock clock;

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(THREADS, THREADS, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY), namedThreads(), new ThreadPoolExecutor.AbortPolicy());

    // 되돌려진 접수는 발행하지 않도록 커밋된 뒤에만 받는다. 넘기기만 하고 곧바로 돌아간다.
    // 큐가 차면 지금은 넘기지 않는다 — 요청 스레드를 붙잡지 않는 것이 먼저이고, 주기 점검이 다시 발행한다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccepted(ExecutionAccepted accepted) {
        try {
            executor.execute(() -> dispatch(accepted.id()));
        } catch (RejectedExecutionException exception) {
            log.warn("발행 큐가 차서 지금은 넘기지 않는다. 주기 점검이 다시 넘긴다: execution={}", accepted.id().value());
        }
    }

    // 넘긴 기록을 남겼으면 true 다. 이미 넘겼거나 끝났거나, 발행하지 못했거나, 버렸으면 false 다.
    public boolean dispatch(ExecutionId id) {
        try {
            Optional<PendingDispatch> pending = executionReader.findPendingDispatch(id);

            if (pending.isEmpty()) {
                return false;
            }

            if (isAbandoned(pending.get())) {
                abandon(pending.get());

                return false;
            }

            runQueuePublisher.submit(pending.get().submit());

            return executionReservationManager.markDispatched(id, clock.instant());
        } catch (RuntimeException exception) {
            log.warn("실행을 넘기지 못했다. 다음 차례에 다시 넘긴다: execution={} {}", id.value(), exception.getMessage());

            return false;
        }
    }

    // 한 건씩 다시 읽어 넘긴다. 목록을 읽은 사이 커밋 직후 시도가 넘긴 실행은 다시 발행하지 않는다.
    public void dispatchPending() {
        for (PendingDispatch pending : executionReader.readPendingDispatches()) {
            dispatch(pending.execution().getId());
        }
    }

    // 꼭 한도만큼 지난 실행은 아직 버리지 않는다.
    private boolean isAbandoned(PendingDispatch pending) {
        return clock.instant().isAfter(pending.acceptedAt().plus(properties.abandonAfter()));
    }

    // 넘기지 못한 채 오래된 실행이다. 혹시 넘어갔을지 몰라 취소를 걸고, 떼어 둔 금액을 돌려주며 플랫폼 탓으로 닫는다.
    // 그 사이 다른 쪽이 넘겼으면 release 가 행을 잠근 뒤 보고 아무것도 하지 않는다.
    private void abandon(PendingDispatch pending) {
        ExecutionId id = pending.execution().getId();

        cancelQuietly(id);

        if (executionReservationManager.release(pending.execution())) {
            log.warn("넘기지 못한 채 한도가 지난 실행을 돌려줬다: execution={}", id.value());
        }
    }

    private void cancelQuietly(ExecutionId id) {
        try {
            runQueuePublisher.cancel(id.value().toString());
        } catch (RuntimeException exception) {
            log.info("버리는 실행에 취소를 걸지 못했다(브로커에 닿지 않음): execution={} {}",
                    id.value(), exception.getMessage());
        }
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }

    private static ThreadFactory namedThreads() {
        AtomicInteger sequence = new AtomicInteger();

        return runnable -> {
            Thread thread = new Thread(runnable, "execution-dispatch-" + sequence.incrementAndGet());
            thread.setDaemon(true);

            return thread;
        };
    }
}
