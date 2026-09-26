package com.aiblockclock.api.v1;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.payment.WithdrawalStatus;
import com.aiblockclock.storage.payment.ChargeRequestEntity;
import com.aiblockclock.storage.payment.ChargeRequestRepository;
import com.aiblockclock.storage.payment.WithdrawalEntity;
import com.aiblockclock.storage.payment.WithdrawalRepository;
import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
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
class CreditHistoryControllerTest {
    private static final String PAYOUT_HASH = "0x" + "b".repeat(64);
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 9, 26, 10, 0);

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChargeRequestRepository chargeRequestRepository;

    @Autowired
    private WithdrawalRepository withdrawalRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private long me;

    private long other;

    private long oldCharge;

    private long payingWithdrawal;

    private long newCharge;

    // 내 것: 충전(10:00) → 출금(10:01, 체인에 보냄) → 충전(10:02). 남의 충전(10:03)은 섞이면 안 된다
    @BeforeEach
    void 두_사용자의_충전과_출금을_시각을_엇갈려_둔다() {
        me = createUser();
        other = createUser();
        oldCharge = saveCharge(me, "1", 0);
        payingWithdrawal = savePayingWithdrawal(me, "5", 1);
        newCharge = saveCharge(me, "2", 2);
        saveCharge(other, "3", 3);
        entityManager.clear();
    }

    @Test
    void 로그인하지_않으면_내역을_볼_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/credits/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 충전과_출금을_요청_시각의_최신순으로_섞어서_준다() throws Exception {
        JsonNode content = content(mockMvc.perform(get("/api/v1/credits/history").with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andReturn());

        assertThat(content).hasSize(3);
        assertThat(row(content, 0)).containsExactly("CHARGE", Long.toString(newCharge));
        assertThat(row(content, 1)).containsExactly("WITHDRAWAL", Long.toString(payingWithdrawal));
        assertThat(row(content, 2)).containsExactly("CHARGE", Long.toString(oldCharge));
    }

    @Test
    void 출금은_상태와_체인_거래_해시를_함께_준다() throws Exception {
        JsonNode withdrawal = content(mockMvc.perform(get("/api/v1/credits/history")
                        .param("type", "WITHDRAWAL")
                        .with(as(me)))
                .andExpect(status().isOk())
                .andReturn()).get(0);

        assertThat(withdrawal.get("status").asString()).isEqualTo("PAYING");
        assertThat(withdrawal.get("amount").asString()).isEqualTo("5.000000");
        assertThat(withdrawal.get("transactionHash").asString()).isEqualTo(PAYOUT_HASH);
        assertThat(withdrawal.get("completedAt").isNull()).isTrue();
    }

    @Test
    void 종류를_주면_그_종류만_준다() throws Exception {
        JsonNode charges = content(mockMvc.perform(get("/api/v1/credits/history")
                        .param("type", "CHARGE")
                        .with(as(me)))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(charges).hasSize(2);
        assertThat(row(charges, 0)).containsExactly("CHARGE", Long.toString(newCharge));
        assertThat(row(charges, 1)).containsExactly("CHARGE", Long.toString(oldCharge));
    }

    @Test
    void 커서로_다음_페이지를_이어_받으면_빠지거나_겹치는_줄이_없다() throws Exception {
        MvcResult first = mockMvc.perform(get("/api/v1/credits/history")
                        .param("size", "2")
                        .with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andReturn();
        String cursor = mapper.readTree(first.getResponse().getContentAsString())
                .get("data").get("nextCursor").asString();

        JsonNode second = content(mockMvc.perform(get("/api/v1/credits/history")
                        .param("size", "2")
                        .param("cursor", cursor)
                        .with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist())
                .andReturn());

        assertThat(content(first)).hasSize(2);
        assertThat(second).hasSize(1);
        assertThat(row(second, 0)).containsExactly("CHARGE", Long.toString(oldCharge));
    }

    @Test
    void 남의_충전_출금은_보이지_않는다() throws Exception {
        mockMvc.perform(get("/api/v1/credits/history")
                        .param("type", "WITHDRAWAL")
                        .with(as(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void 없는_종류를_주면_400이다() throws Exception {
        mockMvc.perform(get("/api/v1/credits/history")
                        .param("type", "RESERVE")
                        .with(as(me)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 한_페이지가_50을_넘으면_400이다() throws Exception {
        mockMvc.perform(get("/api/v1/credits/history")
                        .param("size", "51")
                        .with(as(me)))
                .andExpect(status().isBadRequest());
    }

    private JsonNode content(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString()).get("data").get("content");
    }

    private static List<String> row(JsonNode content, int index) {
        JsonNode item = content.get(index);

        return List.of(item.get("type").asString(), item.get("id").asString());
    }

    private RequestPostProcessor as(long userId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                UserId.of(userId), null, List.of()));
    }

    private long createUser() {
        String walletAddress = "0x" + UUID.randomUUID().toString().replace("-", "") + "00000000";

        return userRepository.saveAndFlush(UserEntity.of(walletAddress)).getId();
    }

    private long saveCharge(long owner, String amount, int minutesAfterBase) {
        long id = chargeRequestRepository.saveAndFlush(ChargeRequestEntity.pending(
                Long.toString(owner),
                "0x1111111111111111111111111111111111111111",
                31221L,
                "0x" + UUID.randomUUID().toString().replace("-", "").repeat(2),
                new BigDecimal(amount))).getId();
        moveCreatedAt("charge_request", id, minutesAfterBase);

        return id;
    }

    private long savePayingWithdrawal(long owner, String amount, int minutesAfterBase) {
        long id = withdrawalRepository.saveAndFlush(WithdrawalEntity.of(UUID.randomUUID(), owner, new BigDecimal(amount),
                "0x2222222222222222222222222222222222222222", WithdrawalStatus.APPROVED)).getId();
        withdrawalRepository.updatePayoutStartIfApproved(id, PAYOUT_HASH, "0xf86c", Instant.now());
        moveCreatedAt("withdrawal", id, minutesAfterBase);

        return id;
    }

    // 저장 시각은 자동으로 찍혀 몇 마이크로초 차이밖에 나지 않는다. 순서를 확실히 하려고 분 단위로 벌려 둔다
    private void moveCreatedAt(String table, long id, int minutesAfterBase) {
        jdbcTemplate.update("update " + table + " set created_at = ? where id = ?",
                Timestamp.valueOf(BASE.plusMinutes(minutesAfterBase)), id);
    }
}
