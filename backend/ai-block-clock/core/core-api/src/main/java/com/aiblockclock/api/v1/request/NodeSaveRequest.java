package com.aiblockclock.api.v1.request;

import jakarta.validation.Valid;
import com.aiblockclock.domain.node.NodeOutput;
import com.aiblockclock.enums.node.AiModel;

import com.aiblockclock.enums.node.OutputKind;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "작업 노드 저장 요청. 생성과 수정에 같은 모양을 쓴다")
public record NodeSaveRequest(
        @Schema(description = "노드 이름. 실행 진행 화면에 그대로 보인다", example = "성적 분석", maxLength = 40)
        @NotBlank @Size(max = 40) String name,

        @Schema(description = "프롬프트 원문", example = "당신은 학원 성적 데이터 분석가입니다.")
        @NotBlank String prompt,

        @Schema(description = "쓸 모델. 비우면 서버 기본 모델", nullable = true)
        AiModel model,

        @Schema(description = "결과를 내는 형식. 비우면 `TEXT`", nullable = true)
        @Valid NodeOutputRequest output
) {
    public AiModel modelOrDefault() {
        return model == null ? AiModel.defaultModel() : model;
    }

    public NodeOutput outputOrDefault() {
        return output == null ? new NodeOutput(OutputKind.TEXT, null) : new NodeOutput(output.kind(), output.sample());
    }
}
