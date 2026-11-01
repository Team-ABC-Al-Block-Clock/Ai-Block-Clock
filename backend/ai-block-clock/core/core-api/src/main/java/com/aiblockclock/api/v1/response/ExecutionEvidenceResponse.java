package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.execution.ExecutionEvidence;

import java.time.Instant;
import java.util.List;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 엔진 원본은 문자열로 저장해 두지만, 응답에서는 JSON 으로 펼쳐 Swagger 에서 바로 읽히게 한다.
// 펼치지 못하는 원본(깨진 JSON)은 펼치지 않고 문자열 그대로 둔다 — 근거는 받은 그대로여야 한다.
public record ExecutionEvidenceResponse(
        ExecutionEvidence.Summary execution,
        ExecutionEvidence.Failure failure,
        List<ExecutionEvidence.ChargeRate> chargeRates,
        List<ExecutionEvidence.Step> steps,
        List<ExecutionEvidence.Settlement> settlements,
        List<ExecutionEvidence.LedgerTransaction> ledger,
        RunRecord runRecord
) {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public static ExecutionEvidenceResponse from(ExecutionEvidence evidence) {
        return new ExecutionEvidenceResponse(evidence.execution(), evidence.failure(), evidence.chargeRates(),
                evidence.steps(), evidence.settlements(), evidence.ledger(), RunRecord.from(evidence.runRecord()));
    }

    public record RunRecord(String closedBy, String runId, String engineStatus, Object response, String sha256,
                            String note, Instant recordedAt) {
        static RunRecord from(ExecutionEvidence.RunRecord record) {
            if (record == null) {
                return null;
            }

            return new RunRecord(record.closedBy(), record.runId(), record.engineStatus(),
                    expand(record.response()), record.sha256(), record.note(), record.recordedAt());
        }

        private static Object expand(String response) {
            if (response == null) {
                return null;
            }

            try {
                JsonNode parsed = MAPPER.readTree(response);

                return parsed == null || parsed.isMissingNode() ? response : parsed;
            } catch (JacksonException exception) {
                return response;
            }
        }
    }
}
