package com.aiblockclock.domain.agent;

public record MarketplaceListing(
        MarketplaceAgent agent, String ownerNickname, AgentGradeMetric metric, boolean favorited) {
    public long executionCount() {
        return metric.countedRuns();
    }

    public double successRate() {
        if (executionCount() == 0) {
            return 0;
        }

        return (double) metric.successfulRuns() / executionCount();
    }
}
