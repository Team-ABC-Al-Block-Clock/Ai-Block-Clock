package com.aiblockclock.api.batch;

import com.aiblockclock.domain.agent.AgentGradeService;
import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.domain.agent.AgentIndexService;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class AgentGradeSchedulerTest {
    private final AgentGradeService agentGradeService = mock(AgentGradeService.class);
    private final AgentIndexService agentIndexService = mock(AgentIndexService.class);
    private final AgentGradeScheduler scheduler = new AgentGradeScheduler(agentGradeService, agentIndexService);

    @Test
    void 대상마다_다시_매기고_다_매긴_뒤에_추천_색인을_한_번_다시_넣는다() {
        AgentId first = AgentId.newId();
        AgentId second = AgentId.newId();
        given(agentGradeService.selectTargets()).willReturn(List.of(first, second));

        scheduler.regradeAll();

        InOrder order = inOrder(agentGradeService, agentIndexService);
        order.verify(agentGradeService).regrade(first);
        order.verify(agentGradeService).regrade(second);
        order.verify(agentIndexService).rebuild();
    }

    @Test
    void 한_에이전트가_실패해도_나머지를_매기고_색인도_다시_넣는다() {
        AgentId broken = AgentId.newId();
        AgentId next = AgentId.newId();
        given(agentGradeService.selectTargets()).willReturn(List.of(broken, next));
        willThrow(new IllegalStateException("db is down")).given(agentGradeService).regrade(broken);

        scheduler.regradeAll();

        InOrder order = inOrder(agentGradeService, agentIndexService);
        order.verify(agentGradeService).regrade(broken);
        order.verify(agentGradeService).regrade(next);
        order.verify(agentIndexService).rebuild();
    }
}
