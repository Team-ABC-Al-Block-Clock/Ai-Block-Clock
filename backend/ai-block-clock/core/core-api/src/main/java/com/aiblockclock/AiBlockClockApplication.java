package com.aiblockclock;

import com.aiblockclock.domain.agent.AgentGradeProperties;
import com.aiblockclock.domain.auth.WalletAuthProperties;
import com.aiblockclock.domain.file.FileStoreProperties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({WalletAuthProperties.class, FileStoreProperties.class, AgentGradeProperties.class})
public class AiBlockClockApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiBlockClockApplication.class, args);
    }

}
