package com.aiblockclock.client.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@EnabledIfEnvironmentVariable(named = "AI_RUNTIME_E2E", matches = "https?://.+")
class AiRuntimeClientE2eTest {
    private static final String WORKFLOW = """
            { "id": "e2e", "name": "성적 리포트",
              "nodes": [
                { "id": "n1", "type": "llm", "name": "성적 분석",
                  "prompt": "학생별 점수 표를 받아 이번 기간과 지난 기간을 비교한다",
                  "output_schema_id": "common.text@1", "owner_id": "u_1" },
                { "id": "n2", "type": "llm", "name": "가정통신문 작성",
                  "prompt": "분석 결과로 가정통신문을 쓴다",
                  "output_schema_id": "common.document@1", "owner_id": "u_1" } ],
              "edges": [ { "from": "n1", "to": "n2" } ],
              "output": { "schema_id": "common.document@1", "template_id": null } }
            """;

    @Test
    @DisplayName("실행을 요청하고 끝날 때까지 따라가 결과를 받는다")
    void followsARunToItsEnd() {
        AiRuntimeClient client = new AiRuntimeClient(
                RestClient.builder().baseUrl(System.getenv("AI_RUNTIME_E2E")).build());

        assertThat(client.health().status()).isEqualTo("ok");

        RunAccepted accepted = client.start(new RunRequest(WORKFLOW, null, null, true));

        assertThat(accepted.runId()).isNotBlank();
        assertThat(accepted.nodeCount()).isEqualTo(2);

        RunStatus finished = pollUntilFinished(client, accepted.runId());

        assertThat(finished.isSucceeded())
                .as("실패 사유: %s", finished.failure())
                .isTrue();
        assertThat(finished.progress().percent()).isEqualTo(100);
        assertThat(finished.nodes()).extracting(RunStatus.Node::status)
                .containsExactly("succeeded", "succeeded");
        assertThat(finished.result().artifactUrl()).contains(accepted.runId());
        assertThat(client.readArtifact(accepted.runId())).isNotBlank();
    }

    private static RunStatus pollUntilFinished(AiRuntimeClient client, String runId) {
        Instant deadline = Instant.now().plus(Duration.ofMinutes(2));
        RunStatus status = client.read(runId);

        while (!status.isFinished() && Instant.now().isBefore(deadline)) {
            sleep();
            status = client.read(runId);
        }

        return status;
    }

    private static void sleep() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
