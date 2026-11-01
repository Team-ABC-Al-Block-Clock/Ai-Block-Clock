package com.aiblockclock.domain.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

// abc.runs.dead 의 x-first-death-reason 헤더 → 실패 코드 (contract 8장). 어느 것이든 플랫폼 탓이다.
class DeadLetterReasonTest {

    @ParameterizedTest
    @CsvSource({"expired, EXPIRED", "delivery_limit, DELIVERY_LIMIT"})
    void 브로커가_붙인_사유를_읽는다(String header, DeadLetterReason expected) {
        assertThat(DeadLetterReason.of(header)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"rejected", "maxlen", "delivery-limit"})
    void 그_밖의_사유나_헤더가_없으면_그_밖으로_본다(String header) {
        assertThat(DeadLetterReason.of(header)).isEqualTo(DeadLetterReason.OTHER);
    }

    @Test
    void 큐에서_만료된_실행은_ABC_QUEUE_EXPIRED_다() {
        assertThat(DeadLetterReason.EXPIRED.failureCode()).isEqualTo("ABC_QUEUE_EXPIRED");
    }

    @Test
    void 재배달_한도를_넘긴_실행은_ABC_RUNTIME_RETRY_EXHAUSTED_다() {
        assertThat(DeadLetterReason.DELIVERY_LIMIT.failureCode()).isEqualTo("ABC_RUNTIME_RETRY_EXHAUSTED");
    }

    @Test
    void 그_밖의_사유로_죽은_실행은_ABC_RUNTIME_DEAD_LETTERED_다() {
        assertThat(DeadLetterReason.OTHER.failureCode()).isEqualTo("ABC_RUNTIME_DEAD_LETTERED");
    }

    // 근거 백필이 닫은 까닭을 실패 코드로 되짚는다(B35). dead letter 의 세 코드만 dead letter 로 본다.
    @ParameterizedTest
    @ValueSource(strings = {"ABC_QUEUE_EXPIRED", "ABC_RUNTIME_RETRY_EXHAUSTED", "ABC_RUNTIME_DEAD_LETTERED"})
    void dead_letter_로_닫은_실행의_실패_코드를_알아본다(String failureCode) {
        assertThat(DeadLetterReason.isFailureCodeOf(failureCode)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"ABC_RUNTIME_STALLED", "ABC_RUNTIME_LOST", "ABC_RUNTIME_UNREACHABLE"})
    void 우리가_끊었거나_넘기지_못한_실행의_실패_코드는_dead_letter_가_아니다(String failureCode) {
        assertThat(DeadLetterReason.isFailureCodeOf(failureCode)).isFalse();
    }
}
