package com.aiblockclock.domain.node;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// V30 은 이미 쌓인 행을 고치는 마이그레이션이라, V29 까지 올린 DB 에 옛 행을 넣은 뒤 올려 본다.
@Tag("container")
class BaseCostMigrationMySqlTest {

    @Test
    void 임시_원가_3_ABC_만_기본_원가로_바꾸고_손으로_넣은_원가는_남긴다() {
        try (MySQLContainer mysql = new MySQLContainer("mysql:8.4").withDatabaseName("base_cost_migration")) {
            mysql.start();
            var dataSource = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Flyway.configure().dataSource(dataSource).target("29").load().migrate();

            node(jdbc, "temporary", "3.000000");
            node(jdbc, "seeded", "0.500000");
            version(jdbc, "all-temporary", 2, "6.000000");
            versionNode(jdbc, "all-temporary", "n1", "3.000000");
            versionNode(jdbc, "all-temporary", "n2", "3.000000");
            version(jdbc, "hand-priced", 2, "1.250000");
            versionNode(jdbc, "hand-priced", "n1", "0.250000");
            versionNode(jdbc, "hand-priced", "n2", "1.000000");

            Flyway.configure().dataSource(dataSource).load().migrate();

            assertThat(baseCostOf(jdbc, "node_definition", "id = 'temporary'")).isEqualByComparingTo("0.018");
            assertThat(baseCostOf(jdbc, "node_definition", "id = 'seeded'")).isEqualByComparingTo("0.5");
            assertThat(baseCostOf(jdbc, "agent_version", "id = 'all-temporary'")).isEqualByComparingTo("0.036");
            assertThat(baseCostOf(jdbc, "agent_version_node", "agent_version_id = 'all-temporary' and node_key = 'n1'"))
                    .isEqualByComparingTo("0.018");
            assertThat(baseCostOf(jdbc, "agent_version", "id = 'hand-priced'")).isEqualByComparingTo("1.25");
            assertThat(baseCostOf(jdbc, "agent_version_node", "agent_version_id = 'hand-priced' and node_key = 'n2'"))
                    .isEqualByComparingTo("1");
        }
    }

    private static void node(JdbcTemplate jdbc, String id, String baseCost) {
        jdbc.update("insert into node_definition (id, owner_id, name, node_type, prompt, model, base_cost, created_at, "
                + "updated_at) values (?, 1, '노드', 'LLM', '프롬프트', 'GEMINI_2_5_FLASH', ?, now(6), now(6))",
                id, new BigDecimal(baseCost));
    }

    private static void version(JdbcTemplate jdbc, String id, int promptNodeCount, String baseCost) {
        jdbc.update("insert into agent (id, owner_id, name, summary, description, created_at, updated_at) "
                + "values (?, 1, '에이전트', '요약', '설명', now(6), now(6))", id);
        jdbc.update("insert into agent_version (id, agent_id, version_no, graph_id, source_workflow_id, source_revision, "
                        + "prompt_node_count, base_cost, tier, publication_state, created_at, updated_at) "
                        + "values (?, ?, 1, 'graph', 'workflow', 1, ?, ?, 'BRONZE', 'PUBLISHED', now(6), now(6))",
                id, id, promptNodeCount, new BigDecimal(baseCost));
    }

    private static void versionNode(JdbcTemplate jdbc, String versionId, String nodeKey, String baseCost) {
        jdbc.update("insert into agent_version_node (agent_version_id, node_key, owner_id, name, node_type, prompt, "
                + "model, base_cost) values (?, ?, 1, '노드', 'LLM', '프롬프트', 'GEMINI_2_5_FLASH', ?)",
                versionId, nodeKey, new BigDecimal(baseCost));
    }

    private static BigDecimal baseCostOf(JdbcTemplate jdbc, String table, String where) {
        return jdbc.queryForObject("select base_cost from " + table + " where " + where, BigDecimal.class);
    }
}
