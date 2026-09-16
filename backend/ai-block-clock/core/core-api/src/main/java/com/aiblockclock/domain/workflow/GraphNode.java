package com.aiblockclock.domain.workflow;

import com.aiblockclock.enums.workflow.SourceType;

public interface GraphNode {
    NodeKey key();

    SourceType sourceType();

    NodeReference reference();
}
