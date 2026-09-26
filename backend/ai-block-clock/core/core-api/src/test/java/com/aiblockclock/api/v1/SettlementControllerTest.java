package com.aiblockclock.api.v1;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.execution.ExecutionStatus;
import com.aiblockclock.storage.execution.ExecutionEntity;
import com.aiblockclock.storage.execution.ExecutionRepository;
import com.aiblockclock.storage.execution.ExecutionSettlementEntity;
import com.aiblockclock.storage.execution.ExecutionSettlementRepository;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SettlementControllerTest {
    private static final long ME = 9701L;
    private static final long OTHER = 9702L;
    private static final long BUYER = 9703L;
    private static final UUID RESEARCH = UUID.randomUUID();
    private static final UUID WRITER = UUID.randomUUID();

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private ExecutionSettlementRepository executionSettlementRepository;

    @Test
    void 로그인하지_않으면_정산_내역과_통계를_볼_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/settlements"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/settlements/statistics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 정산_내역은_최신순이고_실행_단계_에이전트_금액과_자기_실행_여부를_준다() throws Exception {
        UUID sold = executionBy(BUYER);
        UUID mine = executionBy(ME);
        settle(sold, "n1", "시장 조사", ME, RESEARCH, "1.5");
        settle(mine, "n1", "초안 작성", ME, null, "0.25");

        mockMvc.perform(get("/api/v1/settlements").with(as(ME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.content[0].executionId").value(mine.toString()))
                .andExpect(jsonPath("$.data.content[0].nodeName").value("초안 작성"))
                .andExpect(jsonPath("$.data.content[0].agentId").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].amount").value("0.250000"))
                .andExpect(jsonPath("$.data.content[0].selfUse").value(true))
                .andExpect(jsonPath("$.data.content[1].executionId").value(sold.toString()))
                .andExpect(jsonPath("$.data.content[1].stepId").value("n1"))
                .andExpect(jsonPath("$.data.content[1].agentId").value(RESEARCH.toString()))
                .andExpect(jsonPath("$.data.content[1].amount").value("1.500000"))
                .andExpect(jsonPath("$.data.content[1].selfUse").value(false))
                .andExpect(jsonPath("$.data.content[1].settledAt").exists());
    }

    @Test
    void 다른_공급자의_정산은_내역에도_통계에도_보이지_않는다() throws Exception {
        UUID execution = executionBy(BUYER);
        settle(execution, "n1", "시장 조사", OTHER, RESEARCH, "1.5");

        mockMvc.perform(get("/api/v1/settlements").with(as(ME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));

        mockMvc.perform(get("/api/v1/settlements/statistics").with(as(ME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value("0.000000"));
    }

    // 남의 에이전트 ID 를 넣어도 받는 사람 조건이 먼저라 남의 정산은 나오지 않는다.
    @Test
    void 에이전트로_걸러_조회한다() throws Exception {
        UUID execution = executionBy(BUYER);
        settle(execution, "n1", "시장 조사", ME, RESEARCH, "1.5");
        settle(execution, "n2", "초안 작성", ME, WRITER, "0.25");
        settle(execution, "n3", "교정", OTHER, WRITER, "9");

        mockMvc.perform(get("/api/v1/settlements").with(as(ME))
                        .param("agentId", WRITER.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].stepId").value("n2"));
    }

    @Test
    void 조회_기간_밖의_정산은_빠진다() throws Exception {
        settle(executionBy(BUYER), "n1", "시장 조사", ME, RESEARCH, "1.5");
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);

        mockMvc.perform(get("/api/v1/settlements").with(as(ME))
                        .param("from", yesterday.toString())
                        .param("to", yesterday.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void 커서로_다음_페이지를_이어_조회한다() throws Exception {
        UUID execution = executionBy(BUYER);
        for (int step = 1; step <= 3; step++) {
            settle(execution, "n" + step, "노드", ME, RESEARCH, "1");
        }

        MvcResult firstPage = mockMvc.perform(get("/api/v1/settlements").with(as(ME))
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andReturn();

        String nextCursor = dataOf(firstPage).get("nextCursor").asText();

        mockMvc.perform(get("/api/v1/settlements").with(as(ME))
                        .param("size", "2")
                        .param("cursor", nextCursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].stepId").value("n1"))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 기간을_한쪽만_보내면_거절한다() throws Exception {
        mockMvc.perform(get("/api/v1/settlements").with(as(ME))
                        .param("from", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));

        mockMvc.perform(get("/api/v1/settlements/statistics").with(as(ME))
                        .param("to", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    void 페이지_크기가_50을_넘으면_거절한다() throws Exception {
        mockMvc.perform(get("/api/v1/settlements").with(as(ME))
                        .param("size", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 통계의_묶는_단위가_잘못되면_거절한다() throws Exception {
        mockMvc.perform(get("/api/v1/settlements/statistics").with(as(ME))
                        .param("unit", "WEEK"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 수익_통계의_합계는_같은_조건의_내역을_모두_더한_값과_같다() throws Exception {
        UUID first = executionBy(BUYER);
        UUID second = executionBy(BUYER);
        settle(first, "n1", "시장 조사", ME, RESEARCH, "1.5");
        settle(first, "n2", "시장 조사 2", ME, RESEARCH, "0.5");
        settle(second, "n1", "초안 작성", ME, WRITER, "0.25");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        MvcResult history = mockMvc.perform(get("/api/v1/settlements").with(as(ME))
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        BigDecimal historyTotal = BigDecimal.ZERO;
        for (JsonNode row : dataOf(history).get("content")) {
            historyTotal = historyTotal.add(new BigDecimal(row.get("amount").asText()));
        }

        mockMvc.perform(get("/api/v1/settlements/statistics").with(as(ME))
                        .param("from", today.toString())
                        .param("to", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unit").value("DAY"))
                .andExpect(jsonPath("$.data.totalAmount").value(historyTotal.setScale(6).toPlainString()))
                .andExpect(jsonPath("$.data.executionCount").value(2))
                .andExpect(jsonPath("$.data.periods.length()").value(1))
                .andExpect(jsonPath("$.data.periods[0].periodStart").value(today.toString()))
                .andExpect(jsonPath("$.data.periods[0].amount").value("2.250000"))
                .andExpect(jsonPath("$.data.agents.length()").value(2))
                .andExpect(jsonPath("$.data.agents[0].agentId").value(RESEARCH.toString()))
                .andExpect(jsonPath("$.data.agents[0].amount").value("2.000000"))
                .andExpect(jsonPath("$.data.agents[0].executionCount").value(1))
                .andExpect(jsonPath("$.data.agents[1].agentId").value(WRITER.toString()));

        assertThat(historyTotal).isEqualByComparingTo("2.25");
    }

    @Test
    void 받은_것이_없는_기간의_통계는_합계_0과_빈_목록이다() throws Exception {
        settle(executionBy(BUYER), "n1", "시장 조사", ME, RESEARCH, "1.5");
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);

        mockMvc.perform(get("/api/v1/settlements/statistics").with(as(ME))
                        .param("from", yesterday.toString())
                        .param("to", yesterday.toString())
                        .param("unit", "MONTH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unit").value("MONTH"))
                .andExpect(jsonPath("$.data.totalAmount").value("0.000000"))
                .andExpect(jsonPath("$.data.selfUseAmount").value("0.000000"))
                .andExpect(jsonPath("$.data.executionCount").value(0))
                .andExpect(jsonPath("$.data.periods.length()").value(0))
                .andExpect(jsonPath("$.data.agents.length()").value(0));
    }

    private RequestPostProcessor as(long userId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                UserId.of(userId), null, List.of()));
    }

    private UUID executionBy(long requester) {
        UUID id = UUID.randomUUID();
        executionRepository.save(new ExecutionEntity(id, UUID.randomUUID(), UUID.randomUUID(), requester, "run-" + id,
                ExecutionStatus.SUCCEEDED, 1, null, null, null, new BigDecimal("10")));

        return id;
    }

    private void settle(UUID execution, String stepId, String nodeName, long supplier, UUID agent, String amount) {
        executionSettlementRepository.saveAndFlush(new ExecutionSettlementEntity(
                execution, stepId, nodeName, supplier, agent, new BigDecimal(amount)));
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString()).get("data");
    }
}
