package com.aiblockclock.domain.agent;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentGradeService {
    private final AgentReader agentReader;
    private final AgentGradeMetricReader agentGradeMetricReader;
    private final AgentGradeProperties agentGradeProperties;
    private final AgentGradeHistoryWriter agentGradeHistoryWriter;
    private final AgentVersionWriter agentVersionWriter;
    private final AgentTierChangeWriter agentTierChangeWriter;

    // 재산정(PAY-022) 대상은 공개된 에이전트 전부다. 대상이 많지 않아 바뀐 것만 골라내지 않고, 같은 결과면 남기지 않는 쪽으로 막는다.
    public List<AgentId> selectTargets() {
        return agentReader.readPublic().stream()
                .map(PublicAgent::id)
                .toList();
    }

    // 에이전트 하나를 다시 매겨 가격 등급(agent_version.tier)까지 바꾼다. 읽은 재료로 매긴 등급을 이력과 가격에 함께 쓰므로
    // 한 트랜잭션으로 묶는다. 바뀐 버전이 있으면 바뀌기 전 · 뒤 등급과 판매가를 남긴다.
    // 이미 접수한 실행은 접수 때 적은 배율로 정산하므로 여기서 바꾼 등급은 다음 실행부터 붙는다.
    @Transactional
    public AgentGrade regrade(AgentId agent) {
        AgentGrade grade = agentGradeProperties.gradeOf(agentGradeMetricReader.read(agent));
        long gradeHistoryId = agentGradeHistoryWriter.appendIfChanged(grade);

        agentTierChangeWriter.append(gradeHistoryId, agentVersionWriter.modifyTier(agent, grade.tier()));

        return grade;
    }
}
