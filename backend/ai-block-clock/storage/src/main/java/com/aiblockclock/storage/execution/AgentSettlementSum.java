package com.aiblockclock.storage.execution;

import java.math.BigDecimal;
import java.util.UUID;

public record AgentSettlementSum(UUID agentId, BigDecimal amount) {
}
