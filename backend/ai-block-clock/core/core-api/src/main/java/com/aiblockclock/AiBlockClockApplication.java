package com.aiblockclock;

import com.aiblockclock.api.support.auth.AuthWebProperties;
import com.aiblockclock.domain.auth.AuthTokenProperties;
import com.aiblockclock.domain.auth.WalletAuthProperties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({WalletAuthProperties.class, AuthTokenProperties.class, AuthWebProperties.class})
public class AiBlockClockApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiBlockClockApplication.class, args);
    }

}
