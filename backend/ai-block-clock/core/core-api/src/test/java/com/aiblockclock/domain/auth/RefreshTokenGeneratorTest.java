package com.aiblockclock.domain.auth;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenGeneratorTest {
    private final RefreshTokenGenerator refreshTokenGenerator = new RefreshTokenGenerator();

    @Test
    void Refresh_Token_원문은_64글자의_소문자_16진수로_생성한다() {
        String rawRefreshToken = refreshTokenGenerator.generate();

        assertThat(rawRefreshToken).matches("[0-9a-f]{64}");
    }

    @Test
    void 반복_발급할_때_이전_Refresh_Token_원문을_재사용하지_않는다() {
        List<String> rawRefreshTokens = new ArrayList<>();

        for (int index = 0; index < 100; index++) {
            rawRefreshTokens.add(refreshTokenGenerator.generate());
        }

        assertThat(rawRefreshTokens).doesNotHaveDuplicates();
    }
}
