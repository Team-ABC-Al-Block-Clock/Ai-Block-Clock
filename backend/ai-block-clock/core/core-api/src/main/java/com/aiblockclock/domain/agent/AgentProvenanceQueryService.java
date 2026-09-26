package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.user.User;
import com.aiblockclock.domain.user.UserReader;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;

// 마켓의 에이전트 하나가 누구의 것인지 체인 기록으로 보여 준다 (CHN-002, MKT-006).
// 체인을 읽는 조회라 트랜잭션을 열지 않는다. DB 읽기는 각 Reader 가 짧게 끝낸다.
@Service
@RequiredArgsConstructor
public class AgentProvenanceQueryService {
    private final AgentReader agentReader;
    private final UserReader userReader;
    private final AgentProvenanceReader agentProvenanceReader;
    private final AgentProvenanceChainReader agentProvenanceChainReader;
    private final AgentProvenanceProperties agentProvenanceProperties;

    public AgentProvenanceView provenanceOf(AgentId agentId) {
        AgentVersionId publicVersion = agentReader.findPublicVersion(agentId)
                .orElseThrow(() -> new CoreException(ErrorType.MARKETPLACE_AGENT_NOT_FOUND));
        AgentProvenance current = agentProvenanceReader.find(publicVersion)
                .orElseThrow(() -> new CoreException(ErrorType.AGENT_PROVENANCE_NOT_FOUND));
        AgentProvenance first = agentProvenanceReader.readFirst(agentId);
        User owner = ownerOf(agentId);

        if (!current.isConfirmed()) {
            return new AgentProvenanceView(agentId, owner.nickname(), owner.walletAddress(), current,
                    first.getRequestedAt(), null, null,
                    agentProvenanceProperties.chainId(), agentProvenanceProperties.networkName());
        }

        return new AgentProvenanceView(agentId, owner.nickname(), owner.walletAddress(), current,
                first.getRequestedAt(), agentProvenanceChainReader.documentUrl(current),
                agentProvenanceChainReader.find(current).orElse(null),
                agentProvenanceProperties.chainId(), agentProvenanceProperties.networkName());
    }

    // 원작자 행이 없으면 마켓에 내놓을 수 없는 에이전트다. 조회한 사람을 401 로 돌려보내지 않는다
    private User ownerOf(AgentId agentId) {
        List<User> owners = userReader.readAll(List.of(agentReader.read(agentId).getOwner()));

        if (owners.isEmpty()) {
            throw new CoreException(ErrorType.MARKETPLACE_AGENT_NOT_FOUND);
        }

        return owners.get(0);
    }
}
