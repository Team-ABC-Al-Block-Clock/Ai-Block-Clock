package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.user.UserReader;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 체인에 적을 버전 내용을 모은다. 버전 · 에이전트 · 원작자 지갑 · 얼려 둔 노드를 한 스냅샷에서 읽는다.
// 원본 기록을 보낼 때와, 무결성 검증(322)이 지금 내용을 다시 계산할 때 같이 쓴다.
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentVersionMetadataReader {
    private final AgentVersionReader agentVersionReader;
    private final AgentReader agentReader;
    private final UserReader userReader;
    private final AgentVersionNodeReader agentVersionNodeReader;

    public AgentVersionMetadata read(AgentVersionId versionId) {
        AgentVersion version = agentVersionReader.read(versionId);
        Agent agent = agentReader.read(version.getAgentId());
        String ownerWalletAddress = userReader.read(agent.getOwner()).walletAddress();
        List<VersionNode> nodes = List.copyOf(agentVersionNodeReader.read(versionId).values());

        return new AgentVersionMetadata(agent.getId(), versionId, version.getNumber(), ownerWalletAddress, nodes);
    }
}
