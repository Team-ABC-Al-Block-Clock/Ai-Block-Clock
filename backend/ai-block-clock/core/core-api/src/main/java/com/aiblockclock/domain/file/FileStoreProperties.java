package com.aiblockclock.domain.file;

import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

@Validated
@ConfigurationProperties(prefix = "file-store")
public record FileStoreProperties(@NotNull Path root) {
}
