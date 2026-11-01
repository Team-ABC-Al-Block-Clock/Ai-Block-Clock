package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunMode;
import com.aiblockclock.client.ai.SubmitRun;
import com.aiblockclock.domain.node.NodeTestRunId;
import com.aiblockclock.domain.node.RefFile;
import com.aiblockclock.domain.node.RefFileId;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

// 브로커로 보낼 실행 맡기기다 (contract 3장). 파일은 실어 보내지 않고 file-store 의 키만 싣는다 — 런타임이 같은 볼륨을 읽는다.
class RunSubmissionTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final ExecutionId ID = ExecutionId.of(UUID.fromString("6f1c2a9e-0000-4000-8000-000000000001"));
    private static final Instant NOW = Instant.parse("2026-10-16T05:00:00Z");
    private static final String WORKFLOW = """
            {"id":"w1","name":"성적 분석","nodes":[{"id":"n1","type":"llm","prompt":"점수를 비교한다"}],"edges":[]}""";

    @Test
    void 실행은_실행_번호와_run_모드로_맡긴다() {
        SubmitRun run = RunSubmission.forRun(ID, runtime(Map.of()), null, null, "비교해 주세요", true, NOW);

        assertThat(run.executionId()).isEqualTo(ID.value().toString());
        assertThat(run.mode()).isEqualTo(RunMode.RUN);
        assertThat(run.comment()).isEqualTo("비교해 주세요");
        assertThat(run.mock()).isTrue();
        assertThat(run.submittedAt()).isEqualTo(NOW);
    }

    @Test
    void 런타임_워크플로우는_문자열이_아니라_JSON_객체로_싣는다() {
        SubmitRun run = RunSubmission.forRun(ID, runtime(Map.of()), null, null, null, false, NOW);

        assertThat(run.workflow().isObject()).isTrue();
        assertThat(run.workflow()).isEqualTo(MAPPER.readTree(WORKFLOW));
    }

    @Test
    void 검증한_입력은_JSON_객체로_싣는다() {
        SubmitRun run = RunSubmission.forRun(ID, runtime(Map.of()), "{\"brand_name\":\"하루\"}", null, null, false, NOW);

        assertThat(run.input()).isEqualTo(MAPPER.readTree("{\"brand_name\":\"하루\"}"));
    }

    @Test
    void 입력이_없으면_null_이다() {
        assertThat(RunSubmission.forRun(ID, runtime(Map.of()), null, null, null, false, NOW).input()).isNull();
    }

    @Test
    void 입력_파일은_file_store_키로_싣는다() {
        ExecutionFile inputFile = new ExecutionFile("executions/" + ID.value() + "/input.xlsx", "2학기 성적.xlsx", 6);

        SubmitRun run = RunSubmission.forRun(ID, runtime(Map.of()), null, inputFile, null, false, NOW);

        assertThat(run.files()).containsExactly("executions/" + ID.value() + "/input.xlsx");
    }

    @Test
    void 입력_파일이_없으면_빈_목록이다() {
        assertThat(RunSubmission.forRun(ID, runtime(Map.of()), null, null, null, false, NOW).files()).isEmpty();
    }

    @Test
    void 참고_자료는_단계마다_file_store_키로_싣는다() {
        RefFile guide = refFile("nodes/n-1/guide.pdf", "기준표.pdf");
        RefFile form = refFile("nodes/n-1/form.md", "양식.md");
        RefFile other = refFile("nodes/n-2/terms.txt", "약관.txt");

        SubmitRun run = RunSubmission.forRun(ID, runtime(Map.of("n1", List.of(guide, form), "n3", List.of(other))),
                null, null, null, false, NOW);

        assertThat(run.refFiles()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "n1", List.of("nodes/n-1/guide.pdf", "nodes/n-1/form.md"),
                "n3", List.of("nodes/n-2/terms.txt")));
    }

    @Test
    void 참고_자료가_없으면_빈_객체다() {
        assertThat(RunSubmission.forRun(ID, runtime(Map.of()), null, null, null, false, NOW).refFiles()).isEmpty();
    }

    @Test
    void 노드_테스트는_테스트_실행_번호와_test_모드로_맡기고_파일은_싣지_않는다() {
        NodeTestRunId testRunId = NodeTestRunId.of(UUID.fromString("7a2d3b0f-0000-4000-8000-000000000002"));
        RefFile guide = refFile("nodes/n-1/guide.pdf", "기준표.pdf");

        SubmitRun run = RunSubmission.forTest(testRunId, runtime(Map.of("n1", List.of(guide))), "점수를 비교해 주세요",
                false, NOW);

        assertThat(run.executionId()).isEqualTo("7a2d3b0f-0000-4000-8000-000000000002");
        assertThat(run.mode()).isEqualTo(RunMode.TEST);
        assertThat(run.workflow()).isEqualTo(MAPPER.readTree(WORKFLOW));
        assertThat(run.input()).isNull();
        assertThat(run.comment()).isEqualTo("점수를 비교해 주세요");
        assertThat(run.files()).isEmpty();
        assertThat(run.refFiles()).containsExactlyEntriesOf(Map.of("n1", List.of("nodes/n-1/guide.pdf")));
    }

    private static RuntimeWorkflow runtime(Map<String, List<RefFile>> refFiles) {
        return new RuntimeWorkflow(WORKFLOW, refFiles);
    }

    private static RefFile refFile(String key, String filename) {
        return new RefFile(RefFileId.newId(), key, filename, 10);
    }
}
