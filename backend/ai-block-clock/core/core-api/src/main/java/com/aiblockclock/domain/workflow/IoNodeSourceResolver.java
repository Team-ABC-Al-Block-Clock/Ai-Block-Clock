package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.workflow.SourceType;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class IoNodeSourceResolver implements NodeSourceResolver {
    @Override
    public SourceType sourceType() {
        return SourceType.IO;
    }

    @Override
    public Optional<GraphNode> resolve(UserId requester, NodePlacement placement) {
        return Optional.of(new IoNode(placement.key(), placement.ioType(), placement.fields(),
                placement.resultShape(), placement.resultFormat()));
    }
}
