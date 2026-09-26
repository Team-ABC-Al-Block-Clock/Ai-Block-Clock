package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.payment.WithdrawalStatus;
import com.aiblockclock.storage.payment.WithdrawalRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WithdrawalReader {

    private final WithdrawalRepository withdrawalRepository;

    public Withdrawal read(Long withdrawalId) {
        return withdrawalRepository.findById(withdrawalId)
                .map(WithdrawalEntityConverter::toDomain)
                .orElseThrow(() -> new CoreException(ErrorType.WITHDRAWAL_NOT_FOUND));
    }

    public Optional<Withdrawal> find(UserId requester, WithdrawalRequestId requestId) {
        return withdrawalRepository.findByRequesterIdAndRequestId(requester.value(), requestId.value())
                .map(WithdrawalEntityConverter::toDomain);
    }

    public RecentWithdrawals readRecent(UserId requester) {
        boolean anyInProgress = withdrawalRepository.existsByRequesterIdAndStatusIn(
                requester.value(), WithdrawalStatus.inProgressStatuses());

        return new RecentWithdrawals(anyInProgress, withdrawalRepository
                .findLatestCreatedAt(requester.value(), WithdrawalStatus.countedForIntervalStatuses())
                .orElse(null));
    }

    // 심사를 기다리는 신청을 오래된 것부터 준다. 관리자가 위에서부터 차례로 심사한다
    public List<Withdrawal> readAllRequested() {
        return withdrawalRepository.findAllByStatusOrderByCreatedAtAscIdAsc(WithdrawalStatus.REQUESTED).stream()
                .map(WithdrawalEntityConverter::toDomain)
                .toList();
    }
}
