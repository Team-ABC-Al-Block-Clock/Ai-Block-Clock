package com.aiblockclock.domain.node;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NodeDefinitionValidatorTest {
    private static final OwnerId OWNER = OwnerId.of(1L);
    private static final OwnerId OTHER = OwnerId.of(2L);

    @Test
    @DisplayName("소유자만 통과시킨다")
    void onlyOwnerPasses() {
        assertThatCode(() -> NodeDefinitionValidator.ensureOwner(OWNER, OWNER)).doesNotThrowAnyException();
        assertThatThrownBy(() -> NodeDefinitionValidator.ensureOwner(OWNER, OTHER))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.NODE_FORBIDDEN);
    }
}
