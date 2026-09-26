package com.aiblockclock.domain.agent;

public record AgentFavorite(AgentId agentId, boolean favorited, long favoriteCount) {
}
