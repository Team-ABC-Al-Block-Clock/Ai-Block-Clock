package com.aiblockclock.api.support;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import com.aiblockclock.support.response.ApiResult;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

    /**
     * 본문 이전에 걸리는 형식 오류다. 경로변수·쿼리 파라미터가 타입이나 범위에 맞지 않거나 본문을 읽지 못한 경우로,
     * 서버가 깨진 것이 아니므로 500 으로 내보내지 않는다.
     */
    @ExceptionHandler({
            HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResult<Void>> handleMalformedRequest(Exception e) {
        log.warn("요청 형식이 올바르지 않음: {}", e.getMessage());

        return ResponseEntity.status(ErrorType.INVALID_REQUEST.getStatus())
                .body(ApiResult.fail(ErrorType.INVALID_REQUEST, fieldOf(e)));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResult<Void>> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        log.warn("올린 파일이 상한을 넘음: {}", e.getMessage());

        return ResponseEntity.status(ErrorType.FILE_TOO_LARGE.getStatus())
                .body(ApiResult.fail(ErrorType.FILE_TOO_LARGE, null));
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

    private static Map<String, String> fieldOf(Exception e) {
        if (e instanceof MethodArgumentTypeMismatchException mismatch) {
            return Map.of("field", mismatch.getName());
        }

        return null;
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
