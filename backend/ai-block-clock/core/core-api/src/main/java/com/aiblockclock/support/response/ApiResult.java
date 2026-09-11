package com.aiblockclock.support.response;

import com.aiblockclock.support.error.ErrorMessage;
import com.aiblockclock.support.error.ErrorType;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Getter;

@Getter
@Schema(description = "모든 응답이 쓰는 봉투")
public class ApiResult<T> {
    @Schema(description = "요청 처리 결과", example = "SUCCESS")
    private final ResultType status;

    @Schema(description = "성공했을 때의 본문. 실패면 null")
    private final T data;

    @Schema(description = "실패했을 때의 오류. 성공이면 null")
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
