package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowService;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.storage.node.NodeDefinitionRepository;
import com.aiblockclock.storage.payment.LedgerAccountEntity;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import com.aiblockclock.testsupport.NodeDefinitionSeeder;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@SpringBootTest
@Transactional
class CostEstimateServiceIntegrationTest {
    private static final UserId OWNER = UserId.of(9501L);
    private static final UserId OTHER = UserId.of(9502L);

    @Autowired
    private CostEstimateService costEstimateService;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private NodeDefinitionRepository nodeRepository;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Test
    void 남의_워크플로우는_견적을_볼_수_없다() {
        Workflow workflow = saveWorkflow();

        assertThatThrownBy(() -> costEstimateService.quote(workflow.getId(), OTHER))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.WORKFLOW_FORBIDDEN);
    }

    @Test
    void 장부_계정이_없는_사용자는_견적_전액이_부족하다() {
        Workflow workflow = saveWorkflow();

        CostQuote quote = costEstimateService.quote(workflow.getId(), OWNER);

        assertThat(quote.estimate().total().value()).isEqualByComparingTo("6.000000");
        assertThat(quote.available()).isEqualTo("0.000000");
        assertThat(quote.shortage()).isEqualTo("6.000000");
        assertThat(quote.executable()).isFalse();
    }

    @Test
    void 장부에_쌓인_잔액과_견적을_비교해_부족액을_낸다() {
        Workflow workflow = saveWorkflow();
        chargeOwner("4.000000");

        CostQuote quote = costEstimateService.quote(workflow.getId(), OWNER);

        assertThat(quote.available()).isEqualTo("4.000000");
        assertThat(quote.shortage()).isEqualTo("2.000000");
        assertThat(quote.executable()).isFalse();
    }

    @Test
    void 잔액이_넉넉하면_실행할_수_있다고_알린다() {
        Workflow workflow = saveWorkflow();
        chargeOwner("10.000000");

        CostQuote quote = costEstimateService.quote(workflow.getId(), OWNER);

        assertThat(quote.shortage()).isEqualTo("0.000000");
        assertThat(quote.executable()).isTrue();
    }

    // 노드 두 개라 견적은 3 + 3 = 6 ABC 다.
    private Workflow saveWorkflow() {
        return workflowService.save(OWNER, "견적 볼 워크플로우", null,
                List.of(GraphFixtures.placement("n1", NodeDefinitionSeeder.seed(nodeRepository, OWNER.value())),
                        GraphFixtures.placement("n2", NodeDefinitionSeeder.seed(nodeRepository, OWNER.value()))),
                List.of(GraphFixtures.edge("e1", "n1", "n2")));
    }

    // 잔액은 운영 코드와 같은 경로(증감 UPDATE)로만 바꾼다.
    private void chargeOwner(String amount) {
        LedgerAccountEntity account = ledgerAccountRepository.save(
                LedgerAccountEntity.of(LedgerAccountType.ABC_BALANCE, Long.toString(OWNER.value())));

        ledgerAccountRepository.addBalanceDelta(account.getId(), new BigDecimal(amount), Instant.now());
    }
}
