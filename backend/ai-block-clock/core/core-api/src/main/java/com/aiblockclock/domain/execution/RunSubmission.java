package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunMode;
import com.aiblockclock.client.ai.SubmitRun;
import com.aiblockclock.domain.node.NodeTestRunId;
import com.aiblockclock.domain.node.RefFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import tools.jackson.databind.json.JsonMapper;

// 브로커로 보낼 실행 맡기기(SubmitRun, ai/docs/integration-guide.md §4.3)를 만든다.
//   workflow   RuntimeWorkflow 의 JSON 을 객체로 싣는다
//   input      검증한 입력을 객체로 싣는다. 없으면 null
//   files      입력 파일의 file-store 키. 없으면 []
//   ref_files  단계 id → 참고 자료의 file-store 키. 없으면 {}
// 파일은 실어 보내지 않는다. 런타임이 같은 file-store 볼륨을 읽기 전용으로 붙여 키로 찾는다.
public final class RunSubmission {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private RunSubmission() {
    }

    public static SubmitRun forRun(ExecutionId id, RuntimeWorkflow runtime, String input, ExecutionFile inputFile,
                                   String comment, boolean mock, Instant submittedAt) {
        return new SubmitRun(id.value().toString(), RunMode.RUN, MAPPER.readTree(runtime.json()),
                input == null ? null : MAPPER.readTree(input), comment, mock,
                inputFile == null ? List.of() : List.of(inputFile.key()), refFileKeysOf(runtime), submittedAt);
    }

    // 노드 테스트는 입력도 올린 파일도 없다. 노드의 참고 자료만 싣는다.
    public static SubmitRun forTest(NodeTestRunId id, RuntimeWorkflow runtime, String comment, boolean mock,
                                    Instant submittedAt) {
        return new SubmitRun(id.value().toString(), RunMode.TEST, MAPPER.readTree(runtime.json()), null, comment,
                mock, List.of(), refFileKeysOf(runtime), submittedAt);
    }

    private static Map<String, List<String>> refFileKeysOf(RuntimeWorkflow runtime) {
        return runtime.refFiles().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        step -> step.getValue().stream().map(RefFile::key).toList()));
    }
}
