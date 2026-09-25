package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.payment.AbcBalance;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.enums.workflow.SourceType;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CostQuoteTest {

    @Test
    void 잔액이_견적보다_많으면_부족액은_0이고_실행할_수_있다() {
        CostQuote quote = CostQuote.of(estimateOf("9"), balanceOf("10"));

        assertThat(quote.shortage()).isEqualTo("0.000000");
        assertThat(quote.executable()).isTrue();
    }

    @Test
    void 잔액이_견적과_같으면_부족액은_0이고_실행할_수_있다() {
        CostQuote quote = CostQuote.of(estimateOf("9"), balanceOf("9"));

        assertThat(quote.shortage()).isEqualTo("0.000000");
        assertThat(quote.executable()).isTrue();
    }

    @Test
    void 잔액이_모자라면_모자란_만큼을_부족액으로_알린다() {
        CostQuote quote = CostQuote.of(estimateOf("9"), balanceOf("5"));

        assertThat(quote.shortage()).isEqualTo("4.000000");
        assertThat(quote.executable()).isFalse();
    }

    @Test
    void 충전한_적이_없으면_견적_전액이_부족액이다() {
        CostQuote quote = CostQuote.of(estimateOf("9"), AbcBalance.empty());

        assertThat(quote.available()).isEqualTo("0.000000");
        assertThat(quote.shortage()).isEqualTo("9.000000");
        assertThat(quote.executable()).isFalse();
    }

    @Test
    void 실행에_잠긴_금액은_쓸_수_있는_잔액에_넣지_않는다() {
        CostQuote quote = CostQuote.of(estimateOf("9"),
                AbcBalance.of(new BigDecimal("5"), new BigDecimal("100")));

        assertThat(quote.available()).isEqualTo("5.000000");
        assertThat(quote.shortage()).isEqualTo("4.000000");
    }

    @Test
    void 잔액이_모자라면_접수를_막고_견적_API_와_같은_이름으로_금액을_싣는다() {
        CostQuote quote = CostQuote.of(estimateOf("9"), balanceOf("5"));

        assertThatThrownBy(quote::ensureExecutable)
                .isInstanceOfSatisfying(CoreException.class, exception -> {
                    assertThat(exception.getErrorType()).isEqualTo(ErrorType.ABC_BALANCE_INSUFFICIENT);
                    assertThat(exception.getData()).isEqualTo(Map.of(
                            "estimatedAmount", "9.000000",
                            "availableAmount", "5.000000",
                            "shortageAmount", "4.000000"));
                });
    }

    @Test
    void 잔액이_견적과_같으면_접수를_막지_않는다() {
        CostQuote quote = CostQuote.of(estimateOf("9"), balanceOf("9"));

        assertThatCode(quote::ensureExecutable).doesNotThrowAnyException();
    }

    private static CostEstimate estimateOf(String total) {
        return CostEstimate.of(List.of(
                new CostItem(NodeKey.of("n1"), SourceType.NODE, "노드", AbcAmount.of(new BigDecimal(total)))));
    }

    private static AbcBalance balanceOf(String available) {
        return AbcBalance.of(new BigDecimal(available), BigDecimal.ZERO);
    }
}
