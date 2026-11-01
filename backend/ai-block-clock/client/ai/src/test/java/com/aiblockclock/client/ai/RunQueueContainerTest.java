package com.aiblockclock.client.ai;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 실제 RabbitMQ 위에서 토폴로지 선언 · publisher confirm · dead letter 사유를 본다. 이름과 인자는 contract 1장 그대로다.
// 브로커는 같은 이름의 큐를 다른 인자로 선언하면 PRECONDITION_FAILED 로 거절한다. 런타임(Python)이 계약 그대로 선언했을 때
// 백엔드가 먼저 만든 큐와 부딪히지 않는지를, 계약의 인자로 다시 선언해 보는 것으로 확인한다.
@Tag("container")
@SpringBootTest(properties = {"spring.config.import=classpath:ai.yml", "ai.messaging.confirm-timeout=2s"})
@Import(RabbitTestConfiguration.class)
class RunQueueContainerTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Map<String, Map<String, Object>> CONTRACT_QUEUES = Map.of(
            "abc.runs.submit", Map.of("x-queue-type", "quorum", "x-message-ttl", 600_000, "x-delivery-limit", 1,
                    "x-dead-letter-exchange", "abc.runs.dlx"),
            "abc.runs.dead", Map.of("x-queue-type", "quorum", "x-dead-letter-exchange", "abc.runs.dead.dlx"),
            "abc.runs.parking", Map.of("x-queue-type", "quorum"),
            "abc.events.backend", Map.of("x-queue-type", "quorum", "x-single-active-consumer", true,
                    "x-dead-letter-exchange", "abc.events.dlx"),
            "abc.events.backend.dlq", Map.of("x-queue-type", "quorum"));
    private static final Map<String, String> CONTRACT_EXCHANGES = Map.of(
            "abc.runs", "direct", "abc.runs.dlx", "fanout", "abc.runs.dead.dlx", "fanout", "abc.control", "fanout",
            "abc.events", "topic", "abc.events.dlx", "fanout");
    // 테스트마다 선언되어 있는지 보고 비우는 큐다. parking 은 그것을 보는 테스트가 직접 확인하고 비운다.
    private static final List<String> EMPTIED_BEFORE_EACH = List.of(
            "abc.runs.submit", "abc.runs.dead", "abc.events.backend", "abc.events.backend.dlq");
    private static final String PARKING_QUEUE = "abc.runs.parking";

    @Autowired
    private RunQueuePublisher runQueuePublisher;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private RabbitMQContainer rabbit;

    @BeforeEach
    void 계약의_큐가_선언되어_있고_비어_있다() {
        for (String queue : EMPTIED_BEFORE_EACH) {
            assertThat(amqpAdmin.getQueueInfo(queue)).as("백엔드가 %s 큐를 선언하지 않았다", queue).isNotNull();
            amqpAdmin.purgeQueue(queue);
        }
    }

    @Test
    void 계약의_큐를_계약의_인자_그대로_선언해_런타임이_같은_인자로_다시_선언해도_받아들인다() throws Exception {
        withChannel(channel -> {
            for (Map.Entry<String, Map<String, Object>> queue : CONTRACT_QUEUES.entrySet()) {
                channel.queueDeclarePassive(queue.getKey());
                channel.queueDeclare(queue.getKey(), true, false, false, queue.getValue());
            }
        });
    }

    @Test
    void 계약의_exchange_를_계약의_타입_그대로_선언한다() throws Exception {
        withChannel(channel -> {
            for (Map.Entry<String, String> exchange : CONTRACT_EXCHANGES.entrySet()) {
                channel.exchangeDeclarePassive(exchange.getKey());
                channel.exchangeDeclare(exchange.getKey(), exchange.getValue(), true, false, null);
            }
        });
    }

    @Test
    void 숫자_인자를_문자열로_보낸_선언은_같은_큐로_받아들이지_않는다() {
        assertThatThrownBy(() -> withChannel(channel -> {
            channel.queueDeclarePassive("abc.runs.submit");
            channel.queueDeclare("abc.runs.submit", true, false, false, Map.of("x-queue-type", "quorum",
                    "x-message-ttl", "600000", "x-delivery-limit", 1, "x-dead-letter-exchange", "abc.runs.dlx"));
        })).hasStackTraceContaining("PRECONDITION_FAILED");
    }

    @Test
    void SubmitRun_을_보내면_confirm_을_받은_뒤_돌아오고_submit_큐에_계약의_속성과_본문으로_쌓인다() {
        String executionId = UUID.randomUUID().toString();

        runQueuePublisher.submit(submit(executionId));

        Message received = rabbitTemplate.receive("abc.runs.submit", 5_000);
        assertThat(received).isNotNull();
        MessageProperties properties = received.getMessageProperties();
        assertThat(properties.getContentType()).isEqualTo("application/json");
        assertThat(properties.getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(properties.getType()).isEqualTo("SubmitRun");
        assertThat(properties.getMessageId()).isEqualTo(executionId);

        JsonNode body = MAPPER.readTree(received.getBody());
        assertThat(body.get("execution_id").asString()).isEqualTo(executionId);
        assertThat(body.get("mode").asString()).isEqualTo("run");
        assertThat(body.get("workflow").isObject()).isTrue();
    }

    // 없는 exchange 로 보내면 브로커가 채널을 닫고 confirm 은 nack 으로 온다.
    @Test
    void confirm_이_nack_이면_REQUEST_FAILED_다() {
        amqpAdmin.deleteExchange("abc.runs");

        try {
            assertThatThrownBy(() -> runQueuePublisher.submit(submit(UUID.randomUUID().toString())))
                    .isInstanceOfSatisfying(AiClientException.class, exception ->
                            assertThat(exception.getError()).isEqualTo(AiClientError.REQUEST_FAILED));
        } finally {
            ((RabbitAdmin) amqpAdmin).initialize();
        }
    }

    @Test
    void 취소는_abc_control_로_나가_묶인_큐마다_받는다() {
        String executionId = UUID.randomUUID().toString();
        AnonymousQueue runtime = new AnonymousQueue();
        amqpAdmin.declareQueue(runtime);
        amqpAdmin.declareBinding(BindingBuilder.bind(runtime).to(new FanoutExchange("abc.control")));

        runQueuePublisher.cancel(executionId);

        Message received = rabbitTemplate.receive(runtime.getName(), 5_000);
        assertThat(received).isNotNull();
        assertThat(received.getMessageProperties().getType()).isEqualTo("CancelRun");
        assertThat(received.getMessageProperties().getMessageId()).isEqualTo("cancel:" + executionId);
        JsonNode body = MAPPER.readTree(received.getBody());
        assertThat(body.get("execution_id").asString()).isEqualTo(executionId);
        assertThat(Instant.parse(body.get("requested_at").asString())).isNotNull();
    }

    @Test
    void 취소를_confirm_받지_못하면_REQUEST_FAILED_다() {
        amqpAdmin.deleteExchange("abc.control");

        try {
            assertThatThrownBy(() -> runQueuePublisher.cancel(UUID.randomUUID().toString()))
                    .isInstanceOfSatisfying(AiClientException.class, exception ->
                            assertThat(exception.getError()).isEqualTo(AiClientError.REQUEST_FAILED));
        } finally {
            ((RabbitAdmin) amqpAdmin).initialize();
        }
    }

    // 런타임이 붙잡기 전에 큐에서 오래 기다린 실행이다. 큐 TTL(10분)을 기다릴 수 없어 메시지 만료로 같은 길을 태운다.
    @Test
    void 큐에서_만료된_SubmitRun_은_본문_그대로_사유_expired_를_달고_abc_runs_dead_로_간다() {
        String executionId = UUID.randomUUID().toString();
        MessageProperties properties = new MessageProperties();
        properties.setExpiration("100");

        rabbitTemplate.send("abc.runs", "submit", new Message(submitBody(executionId), properties));

        Message dead = rabbitTemplate.receive("abc.runs.dead", 10_000);
        assertThat(dead).isNotNull();
        assertThat((Object) dead.getMessageProperties().getHeader("x-first-death-reason")).hasToString("expired");
        assertThat(MAPPER.readTree(dead.getBody()).get("execution_id").asString()).isEqualTo(executionId);
    }

    // 런타임이 실행 도중 죽어 ack 하지 못하면 다시 배달된다. x-delivery-limit=1 이라 두 번까지만 배달하고 dead letter 로 보낸다.
    @Test
    void 두_번_배달하고도_끝내지_못한_SubmitRun_은_사유_delivery_limit_을_달고_abc_runs_dead_로_간다() throws Exception {
        String executionId = UUID.randomUUID().toString();
        rabbitTemplate.send("abc.runs", "submit", new Message(submitBody(executionId), new MessageProperties()));

        try (Connection connection = rawConnection()) {
            assertThat(takeAndDie(connection)).as("첫 배달").isTrue();
            assertThat(takeAndDie(connection)).as("다시 배달").isTrue();
            assertThat(takeAndDie(connection)).as("세 번째는 배달하지 않는다").isFalse();
        }

        Message dead = rabbitTemplate.receive("abc.runs.dead", 10_000);
        assertThat(dead).isNotNull();
        assertThat((Object) dead.getMessageProperties().getHeader("x-first-death-reason")).hasToString("delivery_limit");
        assertThat(MAPPER.readTree(dead.getBody()).get("execution_id").asString()).isEqualTo(executionId);
    }

    @Test
    void run_으로_시작하는_키의_이벤트는_백엔드_이벤트_큐에_쌓인다() {
        rabbitTemplate.send("abc.events", "run.progress",
                new Message("{\"seq\":1}".getBytes(StandardCharsets.UTF_8), new MessageProperties()));

        assertThat(rabbitTemplate.receive("abc.events.backend", 5_000)).isNotNull();
    }

    // abc.events.backend 는 single active consumer 큐라 RabbitMQ 4 가 basic.get 을 받지 않는다(405).
    // 백엔드 리스너처럼 수동 ack 컨슈머로 받아 다시 넣지 않고 거절한다.
    @Test
    void 백엔드가_거절한_이벤트는_dlq_로_간다() throws Exception {
        rabbitTemplate.send("abc.events", "run.succeeded",
                new Message("{\"seq\":9}".getBytes(StandardCharsets.UTF_8), new MessageProperties()));

        try (Connection connection = rawConnection()) {
            Channel channel = connection.createChannel();
            BlockingQueue<Long> deliveryTags = new LinkedBlockingQueue<>();

            channel.basicConsume("abc.events.backend", false,
                    (consumerTag, delivery) -> deliveryTags.add(delivery.getEnvelope().getDeliveryTag()),
                    consumerTag -> { });
            Long deliveryTag = deliveryTags.poll(5, TimeUnit.SECONDS);

            assertThat(deliveryTag).as("백엔드 이벤트 큐에서 이벤트를 받지 못했다").isNotNull();
            channel.basicReject(deliveryTag, false);
            channel.close();
        }

        Message dead = rabbitTemplate.receive("abc.events.backend.dlq", 10_000);
        assertThat(dead).isNotNull();
        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"seq\":9}");
    }

    // B5 — 백엔드 리스너가 재시도까지 모두 실패하면 다시 넣지 않고 거절한다. 그것이 버려지지 않고 parking lot 에 남는다.
    // 백엔드 리스너는 :core:core-api 에 있어, 여기서는 수동 ack 로 받아 거절하는 것으로 같은 길을 태운다.
    @Test
    void abc_runs_dead_에서_거절한_것은_본문_그대로_사유_rejected_를_달고_abc_runs_parking_에_남는다() throws Exception {
        assertThat(amqpAdmin.getQueueInfo(PARKING_QUEUE)).as("백엔드가 %s 큐를 선언하지 않았다", PARKING_QUEUE).isNotNull();
        amqpAdmin.purgeQueue(PARKING_QUEUE);
        byte[] body = submitBody(UUID.randomUUID().toString());

        rabbitTemplate.send("", "abc.runs.dead", new Message(body, new MessageProperties()));

        try (Connection connection = rawConnection()) {
            Channel channel = connection.createChannel();
            GetResponse delivery = waitForOrNull(() -> channel.basicGet("abc.runs.dead", false));

            assertThat(delivery).as("abc.runs.dead 에서 실행 맡기기를 받지 못했다").isNotNull();
            channel.basicReject(delivery.getEnvelope().getDeliveryTag(), false);
            channel.close();
        }

        Message parked = rabbitTemplate.receive(PARKING_QUEUE, 10_000);
        assertThat(parked).as("거절한 실행 맡기기가 parking lot 에 없다").isNotNull();
        assertThat(parked.getBody()).isEqualTo(body);
        assertThat(parked.getMessageProperties().getXDeathHeader()).anySatisfy(death -> {
            assertThat(death.get("queue")).hasToString("abc.runs.dead");
            assertThat(death.get("reason")).hasToString("rejected");
        });
    }

    private Connection rawConnection() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbit.getHost());
        factory.setPort(rabbit.getAmqpPort());
        factory.setUsername(rabbit.getAdminUsername());
        factory.setPassword(rabbit.getAdminPassword());

        return factory.newConnection();
    }

    // 받아 놓고 ack 하지 않은 채 채널을 닫는다. 런타임 프로세스가 실행 도중 죽은 것과 같다.
    private static boolean takeAndDie(Connection connection) throws Exception {
        Channel channel = connection.createChannel();
        GetResponse delivery = waitForOrNull(() -> channel.basicGet("abc.runs.submit", false));

        channel.close();

        return delivery != null;
    }

    private void withChannel(ChannelWork work) throws Exception {
        try (Connection connection = rawConnection()) {
            Channel channel = connection.createChannel();

            work.run(channel);
        }
    }

    private static GetResponse waitForOrNull(Fetch fetch) throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) {
            GetResponse response = fetch.get();

            if (response != null) {
                return response;
            }

            Thread.sleep(100);
        }

        return null;
    }

    private static SubmitRun submit(String executionId) {
        return new SubmitRun(executionId, RunMode.RUN, MAPPER.readTree("{\"id\":\"w1\",\"nodes\":[],\"edges\":[]}"),
                null, null, true, List.of(), Map.of(), Instant.parse("2026-10-16T05:00:00Z"));
    }

    private static byte[] submitBody(String executionId) {
        return ("{\"execution_id\":\"" + executionId + "\",\"mode\":\"run\",\"workflow\":{},\"input\":null,"
                + "\"comment\":null,\"mock\":true,\"files\":[],\"ref_files\":{},"
                + "\"submitted_at\":\"2026-10-16T05:00:00Z\"}").getBytes(StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface ChannelWork {
        void run(Channel channel) throws Exception;
    }

    @FunctionalInterface
    private interface Fetch {
        GetResponse get() throws Exception;
    }
}
