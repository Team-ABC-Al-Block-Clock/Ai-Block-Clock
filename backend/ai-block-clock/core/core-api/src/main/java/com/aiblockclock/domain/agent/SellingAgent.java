package com.aiblockclock.domain.agent;

import java.time.Instant;

public record SellingAgent(PublicAgent agent, String thumbnailUrl, Instant createdAt) {
}
