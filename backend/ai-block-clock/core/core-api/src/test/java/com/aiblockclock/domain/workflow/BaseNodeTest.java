package com.aiblockclock.domain.workflow;

import com.aiblockclock.enums.workflow.SourceType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

class BaseNodeTest {
    @Test
    @DisplayName("버전을 갖지 않는 평면 참조로 자신을 내놓는다")
    void flattensToReference() {
        UUID sourceId = UUID.randomUUID();

        BaseNode node = new BaseNode(NodeKey.of("n1"), sourceId);

        assertThat(node.sourceType()).isEqualTo(SourceType.NODE);
        assertThat(node.reference()).isEqualTo(NodeReference.of(sourceId));
        assertThat(node.reference().hasVersion()).isFalse();
    }
}
