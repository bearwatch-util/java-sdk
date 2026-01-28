package io.bearwatch.sdk;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BearWatchConfigTest {

    @Test
    void shouldBuildWithDefaults() {
        BearWatchConfig config = BearWatchConfig.builder("bw_test_key").build();

        assertThat(config.getApiKey()).isEqualTo("bw_test_key");
        assertThat(config.getBaseUrl()).isEqualTo("https://api.bearwatch.dev");
        assertThat(config.getTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.getMaxRetries()).isEqualTo(3);
        assertThat(config.getRetryDelay()).isEqualTo(Duration.ofMillis(500));
        assertThat(config.getErrorHandler()).isNull();
    }

    @Test
    void shouldBuildWithCustomValues() {
        BearWatchConfig config = BearWatchConfig.builder("bw_custom_key")
                .baseUrl("https://custom.api.com")
                .timeout(Duration.ofSeconds(60))
                .maxRetries(5)
                .retryDelay(Duration.ofSeconds(1))
                .onError(e -> System.err.println(e.getMessage()))
                .build();

        assertThat(config.getApiKey()).isEqualTo("bw_custom_key");
        assertThat(config.getBaseUrl()).isEqualTo("https://custom.api.com");
        assertThat(config.getTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(config.getMaxRetries()).isEqualTo(5);
        assertThat(config.getRetryDelay()).isEqualTo(Duration.ofSeconds(1));
        assertThat(config.getErrorHandler()).isNotNull();
    }

    @Test
    void shouldRejectNullApiKey() {
        assertThatThrownBy(() -> BearWatchConfig.builder(null).build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectEmptyApiKey() {
        assertThatThrownBy(() -> BearWatchConfig.builder("").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey must not be empty");
    }

    @Test
    void shouldRejectNegativeMaxRetries() {
        assertThatThrownBy(() -> BearWatchConfig.builder("bw_test")
                .maxRetries(-1)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRetries must be non-negative");
    }

    @Test
    void shouldRejectNullBaseUrl() {
        assertThatThrownBy(() -> BearWatchConfig.builder("bw_test")
                .baseUrl(null)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullTimeout() {
        assertThatThrownBy(() -> BearWatchConfig.builder("bw_test")
                .timeout(null)
                .build())
                .isInstanceOf(NullPointerException.class);
    }
}
