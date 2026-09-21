package com.aiblockclock.domain.node;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NodeDefinitionTest {
    private static final OwnerId OWNER = OwnerId.of(1L);
    private static final OwnerId OTHER = OwnerId.of(2L);

    @Test
    @DisplayName("원가는 밖에서 산출해 받는다")
    void baseCostComesFromOutside() {
        NodeDefinition node = NodeDefinitionFixtures.of(OWNER);

        assertThat(node.getSpec().baseCost()).isEqualTo(NodeDefinitionFixtures.COST);
    }
}
