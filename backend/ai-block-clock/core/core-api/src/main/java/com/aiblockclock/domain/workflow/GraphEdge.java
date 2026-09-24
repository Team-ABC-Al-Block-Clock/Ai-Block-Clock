package com.aiblockclock.domain.workflow;

public record GraphEdge(EdgeKey key, NodeKey sourceNodeKey, NodeKey targetNodeKey, Boolean when) {
}
