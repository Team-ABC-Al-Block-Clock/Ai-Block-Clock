package com.aiblockclock.api.support;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import com.aiblockclock.support.response.ApiResult;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class ApiControllerAdvice {
    private static final String LOG_FORMAT = "{} - {}";

    @ExceptionHandler(CoreException.class)
    public ResponseEntity<ApiResult<Void>> handleCoreException(CoreException e) {
        ErrorType errorType = e.getErrorType();
        record(errorType, e);
        return ResponseEntity.status(errorType.getStatus())
                .body(ApiResult.fail(errorType, e.getData()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValidationException(MethodArgumentNotValidException e) {
        Map<String, String> violations = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> violations.putIfAbsent(fieldOf(error), error.getDefaultMessage()));

        log.warn("요청 값 검증 실패: {}", violations);
        return ResponseEntity.status(ErrorType.INVALID_REQUEST.getStatus())
                .body(ApiResult.fail(ErrorType.INVALID_REQUEST, violations));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleUnexpectedException(Exception e) {
        log.error("처리하지 못한 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.fail(ErrorType.INTERNAL_ERROR, null));
    }

    private static String fieldOf(FieldError error) {
        return error.getField();
    }

    private static void record(ErrorType errorType, CoreException e) {
        String name = errorType.name();
        Object data = e.getData();

        switch (errorType.getLogLevel()) {
            case TRACE -> log.trace(LOG_FORMAT, name, data);
            case DEBUG -> log.debug(LOG_FORMAT, name, data);
            case INFO -> log.info(LOG_FORMAT, name, data);
            case WARN -> log.warn(LOG_FORMAT, name, data);
            case ERROR, FATAL -> log.error(LOG_FORMAT, name, data, e);
            case OFF -> {
            }
        }
    }
}
