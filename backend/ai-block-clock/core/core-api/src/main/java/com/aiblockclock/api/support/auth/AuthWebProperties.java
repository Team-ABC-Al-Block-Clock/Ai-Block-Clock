package com.aiblockclock.api.support.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth-web")
public record AuthWebProperties(boolean secureCookie) {
}
