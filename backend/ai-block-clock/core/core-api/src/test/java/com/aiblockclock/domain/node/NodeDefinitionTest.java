package com.aiblockclock.domain.node;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NodeDefinitionTest {
    private static final OwnerId OWNER = OwnerId.of(1L);
    private static final OwnerId OTHER = OwnerId.of(2L);

    @Test
    @DisplayName("소유자는 이름과 프롬프트와 모델을 한 번에 고친다")
    void ownerUpdates() {
        NodeDefinition node = NodeDefinitionFixtures.of(OWNER);

        node.update(OWNER, NodeDefinitionFixtures.spec("가정통신문 작성"),
                Prompt.of("당신은 교육 심리 전문가입니다."));

        assertThat(node.getSpec().name()).isEqualTo("가정통신문 작성");
        assertThat(node.getPrompt().value()).isEqualTo("당신은 교육 심리 전문가입니다.");
    }

    @Test
    @DisplayName("소유자가 아니면 고칠 수 없다")
    void rejectsNonOwner() {
        NodeDefinition node = NodeDefinitionFixtures.of(OWNER);

        assertThatThrownBy(() -> node.update(OTHER, NodeDefinitionFixtures.spec("탈취"), node.getPrompt()))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.NODE_FORBIDDEN);
    }

    @Test
    @DisplayName("원가는 밖에서 산출해 받는다")
    void baseCostComesFromOutside() {
        NodeDefinition node = NodeDefinitionFixtures.of(OWNER);

        assertThat(node.getSpec().baseCost()).isEqualTo(NodeDefinitionFixtures.COST);
    }
}
