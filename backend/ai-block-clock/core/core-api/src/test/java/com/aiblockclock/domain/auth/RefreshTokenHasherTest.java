package com.aiblockclock.domain.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenHasherTest {
    private final RefreshTokenHasher refreshTokenHasher = new RefreshTokenHasher();

    @Test
    void 알려진_SHA256_결과를_소문자_64글자로_계산한다() {
        String refreshTokenHash = refreshTokenHasher.hash("abc");

        assertThat(refreshTokenHash)
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void 원문의_대소문자를_변경하지_않고_계산한다() {
        String refreshTokenHash = refreshTokenHasher.hash("Abc");

        assertThat(refreshTokenHash)
                .isEqualTo("06d90109c8cce34ec0c776950465421e176f08b831a938b3c6e76cb7bee8790b")
                .isNotEqualTo(refreshTokenHasher.hash("abc"));
    }

    @Test
    void 원문의_공백과_한글을_유지하고_UTF8로_계산한다() {
        String refreshTokenHash = refreshTokenHasher.hash(" 토큰🔐 ");

        assertThat(refreshTokenHash)
                .isEqualTo("bb597b160dabbe8734f7052760c9a35068422c3aa039f84cfccbb1669232e8c7");
    }
}
