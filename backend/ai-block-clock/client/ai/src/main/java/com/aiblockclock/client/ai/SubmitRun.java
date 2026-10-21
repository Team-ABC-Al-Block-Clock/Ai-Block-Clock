package com.aiblockclock.client.ai;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;

// 백엔드 → abc.runs / submit 으로 보내는 실행 맡기기다 (ai/docs/integration-guide.md §4.3). 칸 이름은 snake_case 로 나간다.
// executionId 는 런타임의 run_id 가 되는 멱등 키다. 실행은 실행 번호, 노드 테스트는 테스트 실행 번호다.
// workflow · input 은 문자열이 아니라 JSON 객체로 나간다. input 이 없으면 null 이다.
// files · refFiles 는 file-store 볼륨 루트 기준 상대 경로다. 없으면 [] · {} 다.
public record SubmitRun(
        String executionId,
        RunMode mode,
        JsonNode workflow,
        JsonNode input,
        String comment,
        boolean mock,
        List<String> files,
        Map<String, List<String>> refFiles,
        Instant submittedAt
) {
}
