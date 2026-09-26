package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.AiClientError;
import com.aiblockclock.client.ai.AiClientException;
import com.aiblockclock.client.ai.AiRuntimeClient;
import com.aiblockclock.client.ai.RunAccepted;
import com.aiblockclock.domain.node.NodeDefinition;
import com.aiblockclock.domain.node.NodeDefinitionFixtures;
import com.aiblockclock.domain.node.NodeDefinitionReader;
import com.aiblockclock.domain.node.NodeTestRun;
import com.aiblockclock.domain.node.NodeTestRunId;
import com.aiblockclock.domain.node.NodeTestRunManager;
import com.aiblockclock.domain.node.NodeTestRunReader;
import com.aiblockclock.domain.node.RefFileReader;
import com.aiblockclock.domain.node.RefFileStore;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.node.NodeTestRunStatus;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class NodeTestRunServiceTest {
    private static final UserId OWNER = UserId.of(7001L);

    private final NodeTestRunManager manager = mock(NodeTestRunManager.class);
    private final NodeDefinitionReader nodeReader = mock(NodeDefinitionReader.class);
    private final RefFileReader refFileReader = mock(RefFileReader.class);
    private final AiRuntimeClient aiRuntimeClient = mock(AiRuntimeClient.class);
    private final NodeTestRunService service = new NodeTestRunService(manager, mock(NodeTestRunReader.class),
            nodeReader, refFileReader, mock(RefFileStore.class), aiRuntimeClient);

    private NodeDefinition node;

    private NodeTestRun reserved;

    @BeforeEach
    void 노드_하나와_접수한_테스트를_둔다() {
        node = NodeDefinitionFixtures.of(OWNER);
        reserved = new NodeTestRun(NodeTestRunId.newId(), node.getId(), OWNER, null, false,
                NodeTestRunStatus.REQUESTED, null, null, null, Instant.now(), null);

        given(nodeReader.read(node.getId())).willReturn(node);
        given(refFileReader.read(node.getId())).willReturn(List.of());
        given(manager.reserve(node.getId(), OWNER, false)).willReturn(reserved);
    }

    @Test
    void 엔진이_받지_않으면_접수를_지워_하루_횟수를_쓰지_않는다() {
        given(aiRuntimeClient.start(any())).willThrow(new AiClientException(AiClientError.REQUEST_FAILED, "down"));

        assertThatThrownBy(() -> service.start(node.getId(), OWNER, null, false))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType())
                                .isEqualTo(ErrorType.EXECUTION_ENGINE_UNAVAILABLE));

        then(manager).should().discard(reserved.id());
    }

    // 엔진은 이미 돌고 있다. 접수를 지우면 그 실행으로 잰 원가도 결과물도 잃는다.
    @Test
    void 엔진이_받은_뒤에_기록이_실패하면_접수를_지우지_않는다() {
        given(aiRuntimeClient.start(any())).willReturn(new RunAccepted("run-1", "queued", 1, 2000));
        given(manager.dispatch(reserved.id(), "run-1")).willThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.start(node.getId(), OWNER, null, false))
                .isInstanceOf(IllegalStateException.class);

        then(manager).should(never()).discard(any());
    }
}
