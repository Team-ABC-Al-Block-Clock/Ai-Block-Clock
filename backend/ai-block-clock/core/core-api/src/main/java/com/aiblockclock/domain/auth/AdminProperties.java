package com.aiblockclock.domain.auth;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

// 관리자로 로그인하는 지갑 목록이다(ADM-001). 관리자 가입 화면이 없어 운영자가 설정으로 정한다.
// 로그인할 때 목록에 있으면 관리자 권한을 받는다. 목록을 바꾸면 그 지갑이 다시 로그인해야 반영된다.
// 비어 있으면 관리자가 없다. 주소 형식이 틀리면 기동하지 않는다.
@Validated
@ConfigurationProperties(prefix = "admin")
public record AdminProperties(
        @NotNull List<@Pattern(regexp = "^0x[0-9a-fA-F]{40}$", message = "관리자 지갑 주소 형식이 올바르지 않습니다.")
                String> walletAddresses
) {

    // 로그인 주소는 소문자로 저장되지만 설정에는 체크섬 대소문자로 적을 수 있어 대소문자를 가리지 않는다.
    public boolean isAdmin(String walletAddress) {
        return walletAddresses.stream().anyMatch(admin -> admin.equalsIgnoreCase(walletAddress));
    }
}
