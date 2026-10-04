package com.trustdesk.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.trustdesk.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AiProviderPortTest extends AbstractIntegrationTest {

    @Autowired AiProvider provider;

    @Test
    void placeholderProviderIsWiredAndFailsLoudly() {
        assertThat(provider).isInstanceOf(UnconfiguredAiProvider.class);
        assertThat(provider.name()).isEqualTo("unconfigured");
        assertThatThrownBy(() -> provider.complete(new AiRequest("system", "user")))
                .isInstanceOf(AiProviderException.class);
    }

    @Test
    void portCanBeMockedForTests() {
        AiProvider mock = mock(AiProvider.class);
        when(mock.complete(any())).thenReturn(new AiResponse("canned answer"));
        assertThat(mock.complete(new AiRequest(null, "hello")).content()).isEqualTo("canned answer");
    }

    @Test
    void requestRequiresUserContent() {
        assertThatThrownBy(() -> new AiRequest("system", null)).isInstanceOf(NullPointerException.class);
    }
}
