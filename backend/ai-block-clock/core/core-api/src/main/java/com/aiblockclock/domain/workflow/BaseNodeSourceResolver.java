package com.aiblockclock.domain.workflow;

import com.aiblockclock.enums.workflow.SourceType;

import org.springframework.stereotype.Component;

import java.util.Optional;

// 노드 카탈로그가 아직 없어 sourceId 를 검증하지 않고 그대로 BaseNode 를 만든다.
// 그래서 임의의 UUID 도 저장된다. 카탈로그가 붙으면 조회해서 없으면 WORKFLOW_015 로 거부한다.
@Component
public class BaseNodeSourceResolver implements NodeSourceResolver {
    @Override
    public SourceType sourceType() {
        return SourceType.NODE;
    }

    @Override
    public Optional<GraphNode> resolve(NodePlacement placement) {
        return Optional.of(new BaseNode(placement.key(), placement.sourceId()));
    }
}
