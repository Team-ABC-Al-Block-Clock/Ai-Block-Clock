package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.payment.PayoutConversion;

import io.swagger.v3.oas.annotations.media.Schema;

// 옮긴 뒤의 잔액을 함께 주어 화면이 잔액을 다시 묻지 않고 그릴 수 있게 한다
@Schema(description = "정산금을 옮긴 결과")
public record PayoutConversionResponse(
        @Schema(description = "이 요청으로 옮긴 ABC. 같은 requestId 로 다시 보냈으면 처음 옮긴 금액이다", example = "5.000000")
        String convertedAmount,

        @Schema(description = "옮긴 뒤의 ABC 잔액")
        AbcBalanceResponse balance
) {
    public static PayoutConversionResponse from(PayoutConversion conversion) {
        return new PayoutConversionResponse(
                conversion.convertedAmount().value().toPlainString(),
                AbcBalanceResponse.from(conversion.balance())
        );
    }
}
