package com.aiblockclock.domain.payment;

import com.aiblockclock.testsupport.MySqlTestConfiguration;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("container")
@SpringBootTest(properties = {
        "spring.datasource.hikari.transaction-isolation=TRANSACTION_REPEATABLE_READ",
        "spring.datasource.hikari.maximum-pool-size=20",
        "spring.jpa.show-sql=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        ChargeChainFixtures.CHAIN_ID_PROPERTY,
        ChargeChainFixtures.CUSTODY_PROPERTY,
        ChargeChainFixtures.TOKEN_PROPERTY,
        ChargeChainFixtures.CONFIRMATIONS_PROPERTY
})
@Import(MySqlTestConfiguration.class)
public @interface PaymentMySqlTest {
}
