package com.aiblockclock.domain.node;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.node.AiModel;

import java.math.BigDecimal;

public final class NodeDefinitionFixtures {
    public static final AbcAmount COST = AbcAmount.of(new BigDecimal("3.000000"));

    private NodeDefinitionFixtures() {
    }

    public static NodeDefinition of(UserId owner) {
        return NodeDefinition.create(NodeDefinitionId.newId(), owner, "성적 분석",
                Prompt.of("당신은 학원 성적 데이터 분석가입니다."), AiModel.defaultModel(), COST);
    }
}
