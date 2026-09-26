package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.payment.WithdrawalPayoutResult.Outcome;
import com.aiblockclock.enums.payment.WithdrawalStatus;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;

// 승인된 출금을 한 건씩 보관 지갑에서 abcToken 으로 보내고, 보낸 거래의 결과를 끝까지 따라간다(PAY-025 · PAY-026 · CHN-005~008).
// 체인 호출은 트랜잭션 밖에서 하고, 상태를 바꾸는 쓰기만 각자 짧은 트랜잭션으로 한다(ARCHITECTURE 4.2).
//
// 한 번에 한 건만 보낸다. 보관 지갑의 거래 번호(nonce)는 차례대로 써야 해서, 앞 거래가 끝나기 전에 다음 것을 서명하면
// 앞 거래가 사라졌을 때 번호가 꼬인다. 그래서 보낸 출금(지급 중 · 보류)이 있으면 그것이 닫힐 때까지 다음 것을 보내지 않는다.
//
// 두 번 보내지 않는 길은 셋이다.
// ① 보내기 전에 서명한 거래의 해시를 적고 PAYING 으로 바꾼다. 서버가 보낸 직후 멈춰도 무엇을 보냈는지 남는다.
// ② 다시 보낼 때는 적어 둔 거래를 그대로 보낸다. 체인은 같은 해시를 한 번만 처리한다.
// ③ APPROVED → PAYING 은 조건부 UPDATE 라 두 차례가 겹쳐도 한 신청에 거래는 하나만 적힌다.
//
// 결과를 모를 때 금액을 풀지 않는다. 풀었는데 나중에 체인에서 성공하면 토큰도 나가고 정산금도 돌아온다.
// 금액은 체인이 되돌렸다고 답한 거래만 푼다.
@Service
@RequiredArgsConstructor
public class WithdrawalPayoutService {

    private final WithdrawalReader withdrawalReader;
    private final WithdrawalWriter withdrawalWriter;
    private final WithdrawalPayoutManager withdrawalPayoutManager;
    private final OnChainPayoutSender onChainPayoutSender;
    private final WithdrawalPayoutProperties withdrawalPayoutProperties;
    private final Clock clock;

    // 지급을 켜기 전에 한 번 본다. 서명할 지갑이 보관 지갑이 아니면 보낼 토큰이 없는 지갑으로 서명하게 된다
    public void ensureReady() {
        onChainPayoutSender.ensurePayerIsCustody();
    }

    // 보낸 출금을 먼저 따라가 보고, 닫혔으면 다음 승인된 출금을 보낸다
    public WithdrawalPayoutResult payNext() {
        Optional<Withdrawal> sent = withdrawalReader.findSent();

        if (sent.isPresent()) {
            WithdrawalPayoutResult followed = followUp(sent.get());

            if (!followed.outcome().closesPayout()) {
                return followed;
            }
        }

        return withdrawalReader.findNextApproved()
                .map(this::send)
                .orElseGet(WithdrawalPayoutResult::idle);
    }

    private WithdrawalPayoutResult followUp(Withdrawal sent) {
        PayoutTransaction transaction = sent.getPayoutTransaction();

        return switch (onChainPayoutSender.readStatus(transaction)) {
            case SUCCESS -> complete(sent);
            case FAILED -> fail(sent);
            case PENDING -> undecided(sent, Outcome.WAITING);
            // 노드가 모르는 거래다. 보내다 끊겼거나 노드가 버렸다. 적어 둔 거래를 그대로 다시 보낸다 — 같은 거래라 두 번 나가지 않는다
            case NOT_FOUND -> undecided(sent, onChainPayoutSender.submit(transaction)
                    ? Outcome.WAITING
                    : Outcome.UNREACHABLE);
            case UNKNOWN -> undecided(sent, Outcome.UNREACHABLE);
        };
    }

    private WithdrawalPayoutResult complete(Withdrawal sent) {
        try {
            withdrawalPayoutManager.complete(sent, onChainPayoutSender.chainId());
        } catch (CoreException exception) {
            if (exception.getErrorType() != ErrorType.WITHDRAWAL_INVALID_STATE) {
                throw exception;
            }

            // 다른 차례가 같은 성공을 보고 먼저 끝냈다. 장부도 그쪽이 적었다
        }

        return WithdrawalPayoutResult.of(Outcome.PAID, sent);
    }

    // 되돌려진 거래도 블록에 적히며 거래 번호를 썼다. 그래서 이 출금을 닫으면 다음 출금을 다음 번호로 보낼 수 있다
    private WithdrawalPayoutResult fail(Withdrawal sent) {
        try {
            withdrawalPayoutManager.fail(sent);
        } catch (CoreException exception) {
            if (exception.getErrorType() != ErrorType.WITHDRAWAL_INVALID_STATE) {
                throw exception;
            }

            // 다른 차례가 같은 되돌림을 보고 먼저 닫았다. 금액도 그쪽이 돌려놓았다
        }

        return WithdrawalPayoutResult.of(Outcome.FAILED, sent);
    }

    // 아직 결과를 모른다. 보낸 지 오래됐으면 보류로 넘겨 사람이 보게 한다. 보류 뒤에도 차례마다 같은 거래를 다시 확인한다
    private WithdrawalPayoutResult undecided(Withdrawal sent, Outcome outcome) {
        if (!sent.isPayoutOverdue(withdrawalPayoutProperties.holdAfter(), clock.instant())) {
            return WithdrawalPayoutResult.of(outcome, sent);
        }

        sent.holdPayout();

        try {
            withdrawalWriter.changeStatus(sent, WithdrawalStatus.PAYING);
        } catch (CoreException exception) {
            if (exception.getErrorType() != ErrorType.WITHDRAWAL_INVALID_STATE) {
                throw exception;
            }

            // 다른 차례가 먼저 보류로 넘겼거나 닫았다
            return WithdrawalPayoutResult.of(outcome, sent);
        }

        return WithdrawalPayoutResult.of(Outcome.HELD, sent);
    }

    private WithdrawalPayoutResult send(Withdrawal approved) {
        PayoutPreparation preparation = onChainPayoutSender.prepare(approved);

        switch (preparation.outcome()) {
            case CUSTODY_BALANCE_SHORT -> {
                return WithdrawalPayoutResult.of(Outcome.CUSTODY_BALANCE_SHORT, approved);
            }
            case UNREACHABLE -> {
                return WithdrawalPayoutResult.of(Outcome.UNREACHABLE, approved);
            }
            case PREPARED -> approved.startPayout(preparation.transaction());
        }

        try {
            withdrawalWriter.startPayout(approved);
        } catch (CoreException exception) {
            if (exception.getErrorType() != ErrorType.WITHDRAWAL_INVALID_STATE) {
                throw exception;
            }

            // 다른 차례가 먼저 이 출금의 거래를 적었다. 이쪽이 서명한 거래는 보내지 않고 버린다
            return WithdrawalPayoutResult.of(Outcome.WAITING, approved);
        }

        return onChainPayoutSender.submit(preparation.transaction())
                ? WithdrawalPayoutResult.of(Outcome.SUBMITTED, approved)
                : WithdrawalPayoutResult.of(Outcome.UNREACHABLE, approved);
    }
}
