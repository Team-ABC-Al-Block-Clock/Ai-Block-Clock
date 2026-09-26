package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.workflow.SourceType;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ControlNodeSourceResolver implements NodeSourceResolver {
    @Override
    public SourceType sourceType() {
        return SourceType.CONTROL;
    }

    @Override
    public Optional<GraphNode> resolve(UserId requester, NodePlacement placement) {
        return Optional.of(new ControlNode(placement.key(), placement.controlType(), placement.condition()));
    }
}
