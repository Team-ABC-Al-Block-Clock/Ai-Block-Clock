package com.aiblockclock.storage.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OffsetPageableTest {

    @Test
    @DisplayName("offset 이 limit 의 배수가 아니어도 그대로 표현한다")
    void keepsOffsetThatIsNotAMultipleOfLimit() {
        OffsetPageable pageable = OffsetPageable.of(10, 20);

        assertThat(pageable.getOffset()).isEqualTo(10);
        assertThat(pageable.getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("next 는 limit 만큼만 전진한다")
    void nextAdvancesByLimit() {
        assertThat(OffsetPageable.of(10, 20).next().getOffset()).isEqualTo(30);
    }

    @Test
    @DisplayName("previousOrFirst 는 첫 페이지 아래로 내려가지 않는다")
    void previousOrFirstStopsAtZero() {
        assertThat(OffsetPageable.of(5, 20).previousOrFirst().getOffset()).isZero();
        assertThat(OffsetPageable.of(30, 20).previousOrFirst().getOffset()).isEqualTo(10);
    }

    @Test
    @DisplayName("음수 offset 과 0 이하 limit 은 거부한다")
    void rejectsInvalidArguments() {
        assertThatThrownBy(() -> OffsetPageable.of(-1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OffsetPageable.of(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
