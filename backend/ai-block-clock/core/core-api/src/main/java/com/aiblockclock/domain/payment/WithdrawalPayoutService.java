package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.payment.WithdrawalPayoutResult.Outcome;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.Optional;

// 승인된 출금을 한 건씩 보관 지갑에서 abcToken 으로 보낸다(PAY-025 · CHN-005/006).
// 체인 호출은 트랜잭션 밖에서 하고, 상태를 바꾸는 쓰기만 각자 짧은 트랜잭션으로 한다(ARCHITECTURE 4.2).
//
// 한 번에 한 건만 보낸다. 보관 지갑의 거래 번호(nonce)는 차례대로 써야 해서, 앞 거래가 끝나기 전에 다음 것을 서명하면
// 앞 거래가 사라졌을 때 번호가 꼬인다. 그래서 지급 중(PAYING)인 출금이 있으면 그것부터 끝낸다.
//
// 두 번 보내지 않는 길은 셋이다.
// ① 보내기 전에 서명한 거래의 해시를 적고 PAYING 으로 바꾼다. 서버가 보낸 직후 멈춰도 무엇을 보냈는지 남는다.
// ② 다시 보낼 때는 적어 둔 거래를 그대로 보낸다. 체인은 같은 해시를 한 번만 처리한다.
// ③ APPROVED → PAYING 은 조건부 UPDATE 라 두 차례가 겹쳐도 한 신청에 거래는 하나만 적힌다.
@Service
@RequiredArgsConstructor
public class WithdrawalPayoutService {

    private final WithdrawalReader withdrawalReader;
    private final WithdrawalWriter withdrawalWriter;
    private final WithdrawalPayoutManager withdrawalPayoutManager;
    private final OnChainPayoutSender onChainPayoutSender;

    // 지급을 켜기 전에 한 번 본다. 서명할 지갑이 보관 지갑이 아니면 보낼 토큰이 없는 지갑으로 서명하게 된다
    public void ensureReady() {
        onChainPayoutSender.ensurePayerIsCustody();
    }

    // 지급 중인 출금을 먼저 끝내 보고, 끝났으면 다음 승인된 출금을 보낸다
    public WithdrawalPayoutResult payNext() {
        Optional<Withdrawal> paying = withdrawalReader.findPaying();

        if (paying.isPresent()) {
            WithdrawalPayoutResult followed = followUp(paying.get());

            if (followed.outcome() != Outcome.PAID) {
                return followed;
            }
        }

        return withdrawalReader.findNextApproved()
                .map(this::send)
                .orElseGet(WithdrawalPayoutResult::idle);
    }

    private WithdrawalPayoutResult followUp(Withdrawal paying) {
        PayoutTransaction transaction = paying.getPayoutTransaction();

        return switch (onChainPayoutSender.readStatus(transaction)) {
            case SUCCESS -> complete(paying);
            case PENDING -> WithdrawalPayoutResult.of(Outcome.WAITING, paying);
            // 노드가 모르는 거래다. 보내다 끊겼거나 노드가 버렸다. 적어 둔 거래를 그대로 다시 보낸다
            case NOT_FOUND -> onChainPayoutSender.submit(transaction)
                    ? WithdrawalPayoutResult.of(Outcome.WAITING, paying)
                    : WithdrawalPayoutResult.of(Outcome.UNREACHABLE, paying);
            case FAILED -> WithdrawalPayoutResult.of(Outcome.FAILED_ON_CHAIN, paying);
            case UNKNOWN -> WithdrawalPayoutResult.of(Outcome.UNREACHABLE, paying);
        };
    }

    private WithdrawalPayoutResult complete(Withdrawal paying) {
        try {
            withdrawalPayoutManager.complete(paying, onChainPayoutSender.chainId());
        } catch (CoreException exception) {
            if (exception.getErrorType() != ErrorType.WITHDRAWAL_INVALID_STATE) {
                throw exception;
            }

            // 다른 차례가 같은 성공을 보고 먼저 끝냈다. 장부도 그쪽이 적었다
        }

        return WithdrawalPayoutResult.of(Outcome.PAID, paying);
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
