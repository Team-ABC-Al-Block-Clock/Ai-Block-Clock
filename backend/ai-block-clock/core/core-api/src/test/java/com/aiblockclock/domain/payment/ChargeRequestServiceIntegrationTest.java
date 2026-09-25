package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.storage.payment.ChargeRequestRepository;
import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@SpringBootTest(properties = {
        ChargeChainFixtures.CHAIN_ID_PROPERTY,
        ChargeChainFixtures.CUSTODY_PROPERTY,
        ChargeChainFixtures.TOKEN_PROPERTY,
        ChargeChainFixtures.CONFIRMATIONS_PROPERTY
})
class ChargeRequestServiceIntegrationTest {

    @Autowired
    private ChargeRequestService chargeRequestService;

    @Autowired
    private ChargeRequestRepository chargeRequestRepository;

    @Autowired
    private UserRepository userRepository;

    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        List<String> referenceIds = createdUserIds.stream().map(id -> Long.toString(id)).toList();

        chargeRequestRepository.findAll().stream()
                .filter(request -> referenceIds.contains(request.getUserReferenceId()))
                .forEach(chargeRequestRepository::delete);
        userRepository.deleteAllById(createdUserIds);
    }

    @Test
    void 새로운_충전_요청을_로그인한_지갑과_설정된_체인으로_PENDING_저장한다() {
        UserId requester = userWith("0x1111111111111111111111111111111111111111");

        ChargeRequest result = chargeRequestService.createChargeRequest(
                requester,
                "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                new BigDecimal("10")
        );

        assertThat(result.getId()).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ChargeStatus.PENDING);
        assertThat(result.getSenderWalletAddress()).isEqualTo("0x1111111111111111111111111111111111111111");
        assertThat(result.getChainId()).isEqualTo(ChargeChainFixtures.CHAIN_ID);
        assertThat(result.getTransactionHash())
                .isEqualTo("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(result.getRequestedDepositAmount().value()).isEqualByComparingTo("10.000000");
        assertThat(result.getRequestedAt()).isNotNull();
    }

    @Test
    void 같은_사용자가_동일한_트랜잭션으로_재요청하면_기존_요청을_반환한다() {
        UserId requester = userWith("0x4444444444444444444444444444444444444444");
        String transactionHash = "0x" + "b".repeat(64);

        ChargeRequest first = chargeRequestService.createChargeRequest(requester, transactionHash, new BigDecimal("10"));
        ChargeRequest second = chargeRequestService.createChargeRequest(requester, transactionHash, new BigDecimal("10"));

        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    void 다른_사용자가_이미_사용된_트랜잭션으로_요청하면_차단한다() {
        UserId first = userWith("0x5555555555555555555555555555555555555555");
        UserId second = userWith("0x6666666666666666666666666666666666666666");
        String transactionHash = "0x" + "c".repeat(64);

        chargeRequestService.createChargeRequest(first, transactionHash, new BigDecimal("10"));

        assertThatThrownBy(() -> chargeRequestService.createChargeRequest(second, transactionHash, new BigDecimal("10")))
                .isInstanceOfSatisfying(CoreException.class, exception -> assertThat(exception.getErrorType())
                        .isEqualTo(ErrorType.CHARGE_TRANSACTION_ALREADY_USED));
    }

    @Test
    void 보관_지갑으로_로그인한_사용자는_충전할_수_없다() {
        UserId custody = userWith(ChargeChainFixtures.CUSTODY);

        assertThatThrownBy(() -> chargeRequestService.createChargeRequest(custody, "0x" + "e".repeat(64),
                new BigDecimal("10")))
                .isInstanceOfSatisfying(CoreException.class, exception -> assertThat(exception.getErrorType())
                        .isEqualTo(ErrorType.CHARGE_SENDER_IS_CUSTODY));
    }

    @Test
    void 본인의_충전_요청만_조회할_수_있다() {
        UserId owner = userWith("0x7777777777777777777777777777777777777777");
        UserId other = userWith("0x8888888888888888888888888888888888888888");
        ChargeRequest saved = chargeRequestService.createChargeRequest(owner, "0x" + "d".repeat(64),
                new BigDecimal("10"));

        assertThat(chargeRequestService.getOwnedCharge(saved.getId(), owner).getId()).isEqualTo(saved.getId());
        assertThatThrownBy(() -> chargeRequestService.getOwnedCharge(saved.getId(), other))
                .isInstanceOfSatisfying(CoreException.class, exception -> assertThat(exception.getErrorType())
                        .isEqualTo(ErrorType.CHARGE_REQUEST_FORBIDDEN));
    }

    @Test
    void 존재하지_않는_충전_요청은_조회할_수_없다() {
        assertThatThrownBy(() -> chargeRequestService.getOwnedCharge(Long.MAX_VALUE, UserId.of(1L)))
                .isInstanceOfSatisfying(CoreException.class, exception -> assertThat(exception.getErrorType())
                        .isEqualTo(ErrorType.CHARGE_REQUEST_NOT_FOUND));
    }

    private UserId userWith(String walletAddress) {
        Long id = userRepository.saveAndFlush(UserEntity.of(walletAddress)).getId();
        createdUserIds.add(id);

        return UserId.of(id);
    }
}
