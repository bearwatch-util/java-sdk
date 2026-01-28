package io.bearwatch.sdk;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Configuration for the BearWatch SDK client.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * BearWatchConfig config = BearWatchConfig.builder("bw_your_api_key")
 *     .baseUrl("https://api.bearwatch.dev")
 *     .timeout(Duration.ofSeconds(30))
 *     .maxRetries(3)
 *     .build();
 * }</pre>
 */
public class BearWatchConfig {

    private static final String DEFAULT_BASE_URL = "https://api.bearwatch.dev";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final Duration DEFAULT_RETRY_DELAY = Duration.ofMillis(500);

    private final String apiKey;
    private final String baseUrl;
    private final Duration timeout;
    private final int maxRetries;
    private final Duration retryDelay;
    private final Consumer<BearWatchException> errorHandler;

    private BearWatchConfig(Builder builder) {
        this.apiKey = Objects.requireNonNull(builder.apiKey, "apiKey must not be null");
        this.baseUrl = builder.baseUrl;
        this.timeout = builder.timeout;
        this.maxRetries = builder.maxRetries;
        this.retryDelay = builder.retryDelay;
        this.errorHandler = builder.errorHandler;

        if (apiKey.isEmpty()) {
            throw new IllegalArgumentException("apiKey must not be empty");
        }
    }

    /**
     * Creates a new builder with the specified API key.
     *
     * @param apiKey the BearWatch API key (starts with "bw_")
     * @return a new Builder instance
     */
    public static Builder builder(String apiKey) {
        return new Builder(apiKey);
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public Duration getRetryDelay() {
        return retryDelay;
    }

    public Consumer<BearWatchException> getErrorHandler() {
        return errorHandler;
    }

    /**
     * Builder for {@link BearWatchConfig}.
     */
    public static class Builder {
        private final String apiKey;
        private String baseUrl = DEFAULT_BASE_URL;
        private Duration timeout = DEFAULT_TIMEOUT;
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private Duration retryDelay = DEFAULT_RETRY_DELAY;
        private Consumer<BearWatchException> errorHandler = null;

        private Builder(String apiKey) {
            this.apiKey = apiKey;
        }

        /**
         * Sets the base URL for the BearWatch API.
         *
         * @param baseUrl the base URL (default: https://api.bearwatch.dev)
         * @return this builder
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
            return this;
        }

        /**
         * Sets the HTTP request timeout.
         *
         * @param timeout the timeout duration (default: 30 seconds)
         * @return this builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
            return this;
        }

        /**
         * Sets the maximum number of retry attempts for failed requests.
         *
         * @param maxRetries the maximum retries (default: 3)
         * @return this builder
         */
        public Builder maxRetries(int maxRetries) {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries must be non-negative");
            }
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Sets the delay between retry attempts.
         *
         * @param retryDelay the retry delay (default: 500ms)
         * @return this builder
         */
        public Builder retryDelay(Duration retryDelay) {
            this.retryDelay = Objects.requireNonNull(retryDelay, "retryDelay must not be null");
            return this;
        }

        /**
         * Sets a global error handler for async operations.
         *
         * @param errorHandler the error handler callback
         * @return this builder
         */
        public Builder onError(Consumer<BearWatchException> errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return a new BearWatchConfig instance
         */
        public BearWatchConfig build() {
            return new BearWatchConfig(this);
        }
    }
}
