package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.domain.user.UserId;

// 끝난 실행에서 성공한 단계 하나가 그 주인에게 남긴 정산금이다. 수수료가 없어 사용자가 그 단계에 낸 값 전부다.
// nodeName 은 실행할 때 보낸 노드 이름이다. 노드를 고치거나 지워도 무엇으로 벌었는지 남도록 그때 이름을 적는다.
// agent 는 에이전트 안의 단계일 때 그 에이전트다. 캔버스에 바로 놓인 노드는 비어 있다.
public record ExecutionSettlement(String stepId, String nodeName, UserId supplier, AgentId agent, AbcAmount amount) {
}
