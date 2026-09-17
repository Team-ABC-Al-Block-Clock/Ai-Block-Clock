package com.aiblockclock.api.support.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "auth-web")
public record AuthWebProperties(boolean secureCookie, @NotEmpty List<@NotBlank String> allowedOrigins) {

    @AssertTrue(message = "인증 허용 출처는 경로가 없는 HTTP 또는 HTTPS 출처여야 합니다.")
    public boolean isAllowedOriginsValid() {
        if (allowedOrigins == null) {
            return true;
        }

        return allowedOrigins.stream().allMatch(AuthWebProperties::isOrigin);
    }

    private static boolean isOrigin(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(value);
            return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    && uri.getHost() != null && uri.getRawUserInfo() == null
                    && (uri.getRawPath() == null || uri.getRawPath().isEmpty())
                    && uri.getRawQuery() == null && uri.getRawFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
