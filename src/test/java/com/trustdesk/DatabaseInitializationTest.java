package com.trustdesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Starts the application against a database file that does not exist beforehand. */
@SpringBootTest
@ActiveProfiles("test")
class DatabaseInitializationTest {

    private static final Path DB_FILE = Path.of("target", "trustdesk-init-" + UUID.randomUUID() + ".db");
    /** Evaluated at class-load time, before Spring creates the context. */
    private static final boolean DB_FILE_ABSENT_BEFORE_STARTUP = Files.notExists(DB_FILE);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_FILE + "?foreign_keys=on");
    }

    @Autowired JdbcTemplate jdbc;

    @Test
    void startsFromCleanDatabaseAndCreatesFile() {
        assertThat(DB_FILE_ABSENT_BEFORE_STARTUP).isTrue();
        assertThat(DB_FILE).exists();
    }

    @Test
    void createsAllCoreTables() {
        List<String> tables = jdbc.queryForList(
                "select name from sqlite_master where type = 'table'", String.class);
        assertThat(tables).contains("customers", "orders", "tickets", "knowledge_documents", "tool_catalog",
                "eval_cases", "draft_replies", "tool_actions", "approvals", "agent_runs", "eval_runs");
    }

    @Test
    void seedsReferenceDataAndLeavesLaterMilestoneTablesEmpty() {
        assertThat(count("customers")).isEqualTo(6);
        assertThat(count("orders")).isEqualTo(6);
        assertThat(count("tickets")).isEqualTo(8);
        assertThat(count("knowledge_documents")).isEqualTo(8);
        assertThat(count("tool_catalog")).isEqualTo(6);
        assertThat(count("eval_cases")).isEqualTo(8);
        for (String table : List.of("draft_replies", "tool_actions", "approvals", "agent_runs", "eval_runs")) {
            assertThat(count(table)).as(table).isZero();
        }
    }

    @Test
    void foreignKeysAreEnforced() {
        assertThat(jdbc.queryForObject("PRAGMA foreign_keys", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update(
                "insert into tickets (ticket_id, customer_id, channel, subject, body, created_at, status) "
                        + "values ('tkt_x', 'cus_missing', 'email', 's', 'b', '2026-01-01T00:00:00+05:30', 'open')"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("FOREIGN KEY");
    }

    @Test
    void schemaDeclaresTheExpectedForeignKeys() {
        assertThat(foreignKeys("orders")).containsExactlyInAnyOrder("customer_id->customers");
        assertThat(foreignKeys("tickets")).containsExactlyInAnyOrder("customer_id->customers", "order_id->orders");
        assertThat(foreignKeys("eval_cases")).containsExactlyInAnyOrder("ticket_id->tickets");
        assertThat(foreignKeys("draft_replies")).containsExactlyInAnyOrder("ticket_id->tickets");
        assertThat(foreignKeys("tool_actions"))
                .containsExactlyInAnyOrder("ticket_id->tickets", "tool_name->tool_catalog");
        assertThat(foreignKeys("approvals"))
                .containsExactlyInAnyOrder("action_id->tool_actions", "draft_id->draft_replies");
        assertThat(foreignKeys("agent_runs")).containsExactlyInAnyOrder("ticket_id->tickets");
    }

    @Test
    void idempotencyKeyIsUniqueAtDatabaseLevel() {
        List<String> uniqueIndexColumns = jdbc.queryForList(
                "select ii.name from pragma_index_list('tool_actions') il "
                        + "join pragma_index_info(il.name) ii where il.\"unique\" = 1", String.class);
        assertThat(uniqueIndexColumns).contains("idempotency_key");
    }

    private List<String> foreignKeys(String table) {
        return jdbc.query("PRAGMA foreign_key_list(" + table + ")",
                (rs, i) -> rs.getString("from") + "->" + rs.getString("table"));
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }
}
