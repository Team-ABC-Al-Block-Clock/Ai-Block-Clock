package com.aiblockclock;

import com.aiblockclock.domain.agent.AgentGradeProperties;
import com.aiblockclock.domain.agent.AgentProvenanceProperties;
import com.aiblockclock.domain.auth.WalletAuthProperties;
import com.aiblockclock.domain.file.FileStoreProperties;
import com.aiblockclock.domain.node.NodeBaseCostProperties;
import com.aiblockclock.domain.node.NodeTestRunProperties;
import com.aiblockclock.domain.payment.ChargeProperties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({WalletAuthProperties.class, FileStoreProperties.class, ChargeProperties.class,
        AgentGradeProperties.class, AgentProvenanceProperties.class, NodeBaseCostProperties.class,
        NodeTestRunProperties.class})
public class AiBlockClockApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiBlockClockApplication.class, args);
    }

}
