package com.aiblockclock.client.ai;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

// 백엔드가 선언하는 exchange · queue · binding 이다 (ai/docs/integration-guide.md §4.1). 런타임도 같은 인자로 선언하므로 한 글자라도 다르면
// 브로커가 PRECONDITION_FAILED 로 채널을 닫는다. 런타임마다 하나씩 생기는 control 큐는 런타임이 선언한다.
// 숫자 인자는 정수로 싣는다. 문자열 "600000" 은 브로커가 다른 인자로 본다.
//   abc.runs.submit      10분(TTL)이 지나거나 두 번 배달하고도 끝나지 않으면 abc.runs.dlx → abc.runs.dead
//   abc.runs.dead        백엔드 리스너가 재시도까지 모두 실패해 거절한 것은 abc.runs.dead.dlx → abc.runs.parking
//   abc.runs.parking     마지막 칸이다. 더 보낼 곳도 소비자도 없어, 사람이 꺼내 보기 전까지 본문 그대로 남는다
//   abc.events.backend   단일 활성 컨슈머라 이벤트가 보낸 순서대로 온다. 거절한 것은 abc.events.dlx → abc.events.backend.dlq
public final class RunTopology {
    // 백엔드 리스너가 받는 큐다
    public static final String EVENTS_QUEUE = "abc.events.backend";
    public static final String DEAD_QUEUE = "abc.runs.dead";

    static final String RUNS_EXCHANGE = "abc.runs";
    static final String SUBMIT_ROUTING_KEY = "submit";
    static final String CONTROL_EXCHANGE = "abc.control";

    private static final String SUBMIT_QUEUE = "abc.runs.submit";
    private static final String RUNS_DEAD_LETTER_EXCHANGE = "abc.runs.dlx";
    private static final String DEAD_DEAD_LETTER_EXCHANGE = "abc.runs.dead.dlx";
    private static final String PARKING_QUEUE = "abc.runs.parking";
    private static final String EVENTS_EXCHANGE = "abc.events";
    private static final String EVENTS_ROUTING_KEY = "run.#";
    private static final String EVENTS_DEAD_LETTER_EXCHANGE = "abc.events.dlx";
    private static final String EVENTS_DEAD_LETTER_QUEUE = "abc.events.backend.dlq";

    private static final int SUBMIT_TTL_MILLIS = 600_000;
    private static final int SUBMIT_DELIVERY_LIMIT = 1;

    private RunTopology() {
    }

    public static Declarables declarables() {
        DirectExchange runs = ExchangeBuilder.directExchange(RUNS_EXCHANGE).durable(true).build();
        FanoutExchange runsDeadLetter = ExchangeBuilder.fanoutExchange(RUNS_DEAD_LETTER_EXCHANGE).durable(true).build();
        FanoutExchange deadDeadLetter = ExchangeBuilder.fanoutExchange(DEAD_DEAD_LETTER_EXCHANGE).durable(true).build();
        FanoutExchange control = ExchangeBuilder.fanoutExchange(CONTROL_EXCHANGE).durable(true).build();
        TopicExchange events = ExchangeBuilder.topicExchange(EVENTS_EXCHANGE).durable(true).build();
        FanoutExchange eventsDeadLetter = ExchangeBuilder.fanoutExchange(EVENTS_DEAD_LETTER_EXCHANGE).durable(true)
                .build();

        Queue submit = QueueBuilder.durable(SUBMIT_QUEUE)
                .quorum()
                .ttl(SUBMIT_TTL_MILLIS)
                .deliveryLimit(SUBMIT_DELIVERY_LIMIT)
                .deadLetterExchange(RUNS_DEAD_LETTER_EXCHANGE)
                .build();
        Queue dead = QueueBuilder.durable(DEAD_QUEUE)
                .quorum()
                .deadLetterExchange(DEAD_DEAD_LETTER_EXCHANGE)
                .build();
        Queue parking = QueueBuilder.durable(PARKING_QUEUE).quorum().build();
        Queue eventsBackend = QueueBuilder.durable(EVENTS_QUEUE)
                .quorum()
                .singleActiveConsumer()
                .deadLetterExchange(EVENTS_DEAD_LETTER_EXCHANGE)
                .build();
        Queue eventsDeadLetterQueue = QueueBuilder.durable(EVENTS_DEAD_LETTER_QUEUE).quorum().build();

        return new Declarables(
                runs, runsDeadLetter, deadDeadLetter, control, events, eventsDeadLetter,
                submit, dead, parking, eventsBackend, eventsDeadLetterQueue,
                BindingBuilder.bind(submit).to(runs).with(SUBMIT_ROUTING_KEY),
                BindingBuilder.bind(dead).to(runsDeadLetter),
                BindingBuilder.bind(parking).to(deadDeadLetter),
                BindingBuilder.bind(eventsBackend).to(events).with(EVENTS_ROUTING_KEY),
                BindingBuilder.bind(eventsDeadLetterQueue).to(eventsDeadLetter));
    }
}
