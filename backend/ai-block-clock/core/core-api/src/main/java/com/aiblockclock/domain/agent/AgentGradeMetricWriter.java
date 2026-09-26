package com.aiblockclock.domain.agent;

import com.aiblockclock.storage.agent.AgentGradeMetricRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class AgentGradeMetricWriter {
    private final AgentGradeMetricRepository agentGradeMetricRepository;

    // 실행을 닫는 트랜잭션(finish) 안에서만 센다. 닫기가 한 번뿐이라 한 실행을 두 번 세지 않고,
    // 닫기가 되돌려지면 센 것도 함께 되돌려진다.
    // 에이전트를 늘 같은 순서로 센다. 두 실행이 같은 에이전트들을 동시에 세도 서로의 줄을 기다리며 멈추지 않는다.
    @Transactional(propagation = Propagation.MANDATORY)
    public void countRuns(Collection<AgentId> succeeded, Collection<AgentId> failed) {
        Instant measuredAt = Instant.now();

        Stream.concat(succeeded.stream(), failed.stream())
                .distinct()
                .sorted(Comparator.comparing(AgentId::value))
                .forEach(agent -> agentGradeMetricRepository.addRuns(agent.value().toString(),
                        succeeded.contains(agent) ? 1 : 0, failed.contains(agent) ? 1 : 0, measuredAt));
    }
}
