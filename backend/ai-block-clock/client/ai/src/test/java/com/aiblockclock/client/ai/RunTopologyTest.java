package com.aiblockclock.client.ai;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

// 이름 · 타입 · 인자는 contract 1장 표를 그대로 옮긴 것이다. 런타임도 같은 값으로 선언하므로 한 글자도 바꾸지 않는다.
// 숫자 인자는 정수로 보내야 한다. 문자열 "600000" 은 브로커가 다른 인자로 본다.
class RunTopologyTest {
    private final Declarables topology = RunTopology.declarables();

    @Test
    void 계약의_exchange_다섯_개를_타입대로_오래_가게_선언한다() {
        Map<String, Exchange> exchanges = exchanges();

        assertThat(exchanges.values())
                .extracting(Exchange::getName, Exchange::getType, Exchange::isDurable, Exchange::isAutoDelete)
                .containsExactlyInAnyOrder(
                        tuple("abc.runs", "direct", true, false),
                        tuple("abc.runs.dlx", "fanout", true, false),
                        tuple("abc.control", "fanout", true, false),
                        tuple("abc.events", "topic", true, false),
                        tuple("abc.events.dlx", "fanout", true, false));
    }

    @Test
    void 런타임마다_생기는_control_큐는_백엔드가_선언하지_않는다() {
        assertThat(queues().keySet()).containsExactlyInAnyOrder(
                "abc.runs.submit", "abc.runs.dead", "abc.events.backend", "abc.events.backend.dlq");
    }

    @Test
    void 큐는_모두_오래_가고_혼자_쓰지_않고_저절로_지워지지_않는다() {
        assertThat(queues().values())
                .allSatisfy(queue -> {
                    assertThat(queue.isDurable()).isTrue();
                    assertThat(queue.isExclusive()).isFalse();
                    assertThat(queue.isAutoDelete()).isFalse();
                });
    }

    @Test
    void submit_큐는_quorum_이고_10분이_지나거나_두_번_배달하고도_끝나지_않으면_abc_runs_dlx_로_보낸다() {
        Map<String, Object> arguments = queues().get("abc.runs.submit").getArguments();

        assertThat(arguments).containsOnlyKeys(
                "x-queue-type", "x-message-ttl", "x-delivery-limit", "x-dead-letter-exchange");
        assertThat(arguments.get("x-queue-type")).isEqualTo("quorum");
        assertWholeNumber(arguments.get("x-message-ttl"), 600_000L);
        assertWholeNumber(arguments.get("x-delivery-limit"), 1L);
        assertThat(arguments.get("x-dead-letter-exchange")).isEqualTo("abc.runs.dlx");
    }

    @Test
    void dead_큐는_quorum_이다() {
        assertThat(queues().get("abc.runs.dead").getArguments())
                .containsExactlyEntriesOf(Map.of("x-queue-type", "quorum"));
    }

    @Test
    void 백엔드_이벤트_큐는_quorum_단일_활성_컨슈머이고_거절한_것은_abc_events_dlx_로_보낸다() {
        Map<String, Object> arguments = queues().get("abc.events.backend").getArguments();

        assertThat(arguments).containsOnlyKeys(
                "x-queue-type", "x-single-active-consumer", "x-dead-letter-exchange");
        assertThat(arguments.get("x-queue-type")).isEqualTo("quorum");
        assertThat(arguments.get("x-single-active-consumer")).isEqualTo(true);
        assertThat(arguments.get("x-dead-letter-exchange")).isEqualTo("abc.events.dlx");
    }

    @Test
    void 이벤트_dlq_는_quorum_이다() {
        assertThat(queues().get("abc.events.backend.dlq").getArguments())
                .containsExactlyEntriesOf(Map.of("x-queue-type", "quorum"));
    }

    @Test
    void 큐를_계약의_exchange_에_묶는다() {
        assertThat(topology.getDeclarablesByType(Binding.class))
                .allSatisfy(binding -> assertThat(binding.getDestinationType()).isEqualTo(Binding.DestinationType.QUEUE))
                .extracting(Binding::getDestination, Binding::getExchange)
                .containsExactlyInAnyOrder(
                        tuple("abc.runs.submit", "abc.runs"),
                        tuple("abc.runs.dead", "abc.runs.dlx"),
                        tuple("abc.events.backend", "abc.events"),
                        tuple("abc.events.backend.dlq", "abc.events.dlx"));
    }

    @Test
    void submit_큐는_submit_키로_이벤트_큐는_run_으로_시작하는_모든_키로_받는다() {
        Map<String, Binding> byQueue = topology.getDeclarablesByType(Binding.class).stream()
                .collect(Collectors.toMap(Binding::getDestination, Function.identity()));

        assertThat(byQueue.get("abc.runs.submit").getRoutingKey()).isEqualTo("submit");
        assertThat(byQueue.get("abc.events.backend").getRoutingKey()).isEqualTo("run.#");
    }

    private Map<String, Exchange> exchanges() {
        return topology.getDeclarablesByType(Exchange.class).stream()
                .collect(Collectors.toMap(Exchange::getName, Function.identity()));
    }

    private Map<String, Queue> queues() {
        return topology.getDeclarablesByType(Queue.class).stream()
                .collect(Collectors.toMap(Queue::getName, Function.identity()));
    }

    private static void assertWholeNumber(Object actual, long expected) {
        assertThat(actual).isInstanceOfAny(Integer.class, Long.class);
        assertThat(((Number) actual).longValue()).isEqualTo(expected);
    }
}
