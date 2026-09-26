package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.workflow.GraphId;
import com.aiblockclock.enums.agent.Tier;
import com.aiblockclock.storage.agent.AgentRepository;
import com.aiblockclock.storage.agent.AgentVersionEntity;
import com.aiblockclock.storage.agent.AgentVersionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AgentVersionWriter {
    private final AgentVersionRepository agentVersionRepository;
    private final AgentRepository agentRepository;

    @Transactional
    public void append(AgentVersion version, GraphId graphId) {
        agentVersionRepository.save(AgentVersionEntityConverter.toEntity(
                version, agentRepository.getReferenceById(version.getAgentId().value()), graphId));
    }

    // 등급은 에이전트에 하나라 그 에이전트의 모든 버전을 같은 등급으로 맞춘다. 옛 버전에 고정된 워크플로우도 같은 할증을 낸다.
    // 이미 그 등급인 버전은 건드리지 않고, 바뀐 버전만 돌려준다.
    @Transactional
    public List<AgentTierChange> modifyTier(AgentId agent, Tier tier) {
        List<AgentTierChange> changes = new ArrayList<>();

        for (AgentVersionEntity version : agentVersionRepository.findAllByAgentIdOrderByVersionNoAsc(agent.value())) {
            if (version.getTier() == tier) {
                continue;
            }

            changes.add(new AgentTierChange(agent, AgentVersionId.of(version.getId()),
                    AbcAmount.of(version.getBaseCost()), version.getTier(), tier));
            version.changeTier(tier);
        }

        return changes;
    }
}
