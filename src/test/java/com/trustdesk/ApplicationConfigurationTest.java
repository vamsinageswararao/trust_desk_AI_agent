package com.trustdesk;

import static org.assertj.core.api.Assertions.assertThat;

import com.trustdesk.config.TrustDeskProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ApplicationConfigurationTest extends AbstractIntegrationTest {

    @Autowired TrustDeskProperties properties;

    @Test
    void bindsConfigurableDemoTokenAndSeedSettings() {
        assertThat(properties.auth().token()).isEqualTo(TOKEN);
        assertThat(properties.seed().enabled()).isTrue();
        assertThat(properties.seed().dataDir()).isEqualTo("./data");
    }

    @Test
    void tokenIsNotPrintedByToString() {
        assertThat(properties.toString()).doesNotContain(TOKEN);
    }
}
