package com.aiblockclock;

import com.aiblockclock.domain.auth.WalletAuthProperties;
import com.aiblockclock.domain.file.FileStoreProperties;
import com.aiblockclock.domain.payment.ChargeProperties;
import com.aiblockclock.domain.payment.WithdrawalProperties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({WalletAuthProperties.class, FileStoreProperties.class, ChargeProperties.class,
        WithdrawalProperties.class})
public class AiBlockClockApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiBlockClockApplication.class, args);
    }

}
