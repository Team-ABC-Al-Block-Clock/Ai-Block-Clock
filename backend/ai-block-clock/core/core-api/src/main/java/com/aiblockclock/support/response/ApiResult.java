package com.aiblockclock.support.response;

import com.aiblockclock.support.error.ErrorMessage;
import com.aiblockclock.support.error.ErrorType;
import lombok.Getter;

@Getter
public class ApiResult<T> {
    private final ResultType status;
    private final T data;
    private final ErrorMessage error;

    private ApiResult(ResultType status, T data, ErrorMessage error) {
        this.status = status;
        this.data = data;
        this.error = error;
    }

    public static ApiResult<Void> success() {
        return new ApiResult<>(ResultType.SUCCESS, null, null);
    }

    public static <S> ApiResult<S> success(S data) {
        return new ApiResult<>(ResultType.SUCCESS, data, null);
    }

    public static <S> ApiResult<S> fail(ErrorType error, Object errorData) {
        return new ApiResult<>(ResultType.FAIL, null, new ErrorMessage(error, errorData));
    }
}
