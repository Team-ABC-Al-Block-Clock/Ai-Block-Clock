package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.node.NodeOutput;
import com.aiblockclock.domain.node.Prompt;
import com.aiblockclock.domain.node.RefFile;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.enums.workflow.InputSource;

import java.util.List;

// placedKey 는 이 단계가 나온 캔버스 자리다. 에이전트 안의 단계는 그 에이전트 자리에 속한다.
// 엔진은 펼친 단계만 알므로, 끝난 뒤 값을 셀 때 단계를 캔버스 자리로 접는 데 쓴다.
// baseCost 는 이 단계 노드의 원가다. 에이전트 안의 단계는 버전에 굳힌 원가다. 단가 = 원가 × 자리의 배율.
public record PlannedPrompt(
        NodeKey placedKey, String name, UserId owner, Prompt prompt, AiModel model, NodeOutput output,
        InputSource inputSource, List<RefFile> refFiles, AbcAmount baseCost) {
    public PlannedPrompt {
        refFiles = List.copyOf(refFiles);
    }
}
