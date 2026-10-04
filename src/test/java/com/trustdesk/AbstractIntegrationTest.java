package com.trustdesk;

import java.util.UUID;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Shared Spring context backed by a brand-new SQLite file per test run (never ./trustdesk.db).
 * Do not add @MockBean/@TestPropertySource to subclasses: that would create a second context on the same DB file.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    public static final String TOKEN = "test-demo-token"; // matches application-test.properties
    public static final String BEARER = "Bearer " + TOKEN;

    private static final String DB_URL =
            "jdbc:sqlite:target/trustdesk-it-" + UUID.randomUUID() + ".db?foreign_keys=on";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DB_URL);
    }
}
