package com.aiblockclock.domain.file;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileStore {
    private final FileStoreProperties properties;

    public void write(String key, byte[] content) {
        Path path = locate(key);

        try {
            Files.createDirectories(path.getParent());
            Files.write(path, content);
        } catch (IOException exception) {
            log.error("파일을 저장하지 못했다: {}", path, exception);

            throw new CoreException(ErrorType.FILE_NOT_STORED);
        }
    }

    public Path locate(String key) {
        return properties.root().resolve(key);
    }

    public void erase(String key) {
        if (key == null) {
            return;
        }

        Path path = locate(key);

        try {
            Files.deleteIfExists(path);
            Files.deleteIfExists(path.getParent());
        } catch (IOException exception) {
            log.warn("저장한 파일을 지우지 못했다: {}", path, exception);
        }
    }
}
