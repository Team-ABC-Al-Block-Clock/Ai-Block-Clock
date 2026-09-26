package com.aiblockclock.api.v1.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "출금 신청")
public record WithdrawalCreateRequest(
        @Schema(description = """
                신청 버튼을 누를 때 화면이 만드는 UUID. 같은 누름을 다시 보낼 때(재시도 · 연타)는 같은 값을 보낸다. \
                같은 값이 다시 오면 새로 묶지 않고 앞서 접수한 신청을 돌려준다. 새 신청은 새 값으로 보낸다""",
                example = "0b6f3c2e-9d41-4a7e-8c55-1e2f3a4b5c6d")
        @NotNull UUID requestId,

        // 장부 금액 칸(DECIMAL(41, 6))에 담기는 범위다. 한 번에 신청할 수 있는 범위는 정책에서 본다
        @Schema(description = "출금할 ABC (= 받을 abcToken 개수). 소수 6자리까지", example = "10")
        @NotNull
        @Positive
        @Digits(integer = 32, fraction = 6, message = "출금 금액은 소수 6자리까지입니다.")
        BigDecimal amount
) {
}
