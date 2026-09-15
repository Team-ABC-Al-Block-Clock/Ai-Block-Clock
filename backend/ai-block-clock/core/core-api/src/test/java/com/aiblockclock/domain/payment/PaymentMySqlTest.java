package com.aiblockclock.domain.payment;

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
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(PaymentMySqlTestConfiguration.class)
@interface PaymentMySqlTest {
}
