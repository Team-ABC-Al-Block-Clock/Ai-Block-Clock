package com.aiblockclock.domain.agent;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentGradeService {
    private final AgentGradeMetricReader agentGradeMetricReader;
    private final AgentGradeProperties agentGradeProperties;
    private final AgentGradeHistoryWriter agentGradeHistoryWriter;

    // 에이전트 하나에 점수와 등급을 매기고 이력에 남긴다. 읽은 재료로 매긴 결과를 쓰므로 한 트랜잭션으로 묶는다.
    // 가격에 쓰는 등급(agent_version.tier)은 바꾸지 않는다. 언제 누구를 매길지와 가격 반영은 재산정 배치(PAY-022)가 정한다.
    @Transactional
    public AgentGrade grade(AgentId agent) {
        AgentGrade grade = agentGradeProperties.gradeOf(agentGradeMetricReader.read(agent));

        agentGradeHistoryWriter.append(grade);

        return grade;
    }
}
