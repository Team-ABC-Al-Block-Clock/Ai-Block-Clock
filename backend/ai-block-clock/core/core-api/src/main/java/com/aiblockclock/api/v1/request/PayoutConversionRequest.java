package com.aiblockclock.api.v1.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "정산금을 쓸 수 있는 ABC 로 옮기기")
public record PayoutConversionRequest(
        @Schema(description = """
                옮기기 버튼을 누를 때 화면이 만드는 UUID. 같은 누름을 다시 보낼 때(재시도 · 연타)는 같은 값을 보낸다. \
                같은 값이 다시 오면 또 옮기지 않고 앞서 옮긴 결과를 돌려준다. 새로 옮길 때는 새 값으로 보낸다""",
                example = "5d2c8a1e-7b3f-4e6a-9c10-2f4e6a8b0c12")
        @NotNull UUID requestId,

        // 장부 금액 칸(DECIMAL(41, 6))에 담기는 범위다. 정산금보다 많은지는 도메인에서 본다
        @Schema(description = "옮길 ABC. 소수 6자리까지", example = "5")
        @NotNull
        @Positive
        @Digits(integer = 32, fraction = 6, message = "옮길 금액은 소수 6자리까지입니다.")
        BigDecimal amount
) {
}
