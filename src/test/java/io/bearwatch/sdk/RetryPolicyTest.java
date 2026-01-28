package io.bearwatch.sdk;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for retry policy behavior.
 */
class RetryPolicyTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    /**
     * Verifies that ping() DOES retry on server error.
     */
    @Test
    void pingShouldRetryOnServerError() throws InterruptedException {
        // First request fails, second succeeds
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));
        server.enqueue(successResponse());

        BearWatchConfig config = BearWatchConfig.builder("bw_test_api_key")
                .baseUrl(server.url("/").toString().replaceAll("/$", ""))
                .timeout(Duration.ofSeconds(2))
                .maxRetries(3)
                .retryDelay(Duration.ofMillis(10))
                .build();

        BearWatch client = BearWatch.create(config);

        try {
            // Should succeed after retry
            var response = client.ping("job-123");
            assertThat(response.getRunId()).isEqualTo("run-abc");

            // Verify TWO requests were made (1 failure + 1 success)
            assertThat(server.getRequestCount()).isEqualTo(2);
        } finally {
            client.close();
        }
    }

    /**
     * Verifies that complete() retries on server error.
     */
    @Test
    void completeShouldRetryOnServerError() throws InterruptedException {
        // First request fails, second succeeds
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));
        server.enqueue(successResponse());

        BearWatchConfig config = BearWatchConfig.builder("bw_test_api_key")
                .baseUrl(server.url("/").toString().replaceAll("/$", ""))
                .timeout(Duration.ofSeconds(2))
                .maxRetries(3)
                .retryDelay(Duration.ofMillis(10))
                .build();

        BearWatch client = BearWatch.create(config);

        try {
            var response = client.complete("job-123");
            assertThat(response.getRunId()).isEqualTo("run-abc");

            // Verify TWO requests were made
            assertThat(server.getRequestCount()).isEqualTo(2);
        } finally {
            client.close();
        }
    }

    /**
     * Verifies that fail() retries on server error.
     */
    @Test
    void failShouldRetryOnServerError() throws InterruptedException {
        // First request fails, second succeeds
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));
        server.enqueue(failedResponse());

        BearWatchConfig config = BearWatchConfig.builder("bw_test_api_key")
                .baseUrl(server.url("/").toString().replaceAll("/$", ""))
                .timeout(Duration.ofSeconds(2))
                .maxRetries(3)
                .retryDelay(Duration.ofMillis(10))
                .build();

        BearWatch client = BearWatch.create(config);

        try {
            var response = client.fail("job-123", "Error occurred");
            assertThat(response.getRunId()).isEqualTo("run-def");

            // Verify TWO requests were made
            assertThat(server.getRequestCount()).isEqualTo(2);
        } finally {
            client.close();
        }
    }

    private MockResponse successResponse() {
        return new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"success\":true,\"data\":{\"runId\":\"run-abc\",\"jobId\":\"job-123\",\"status\":\"SUCCESS\",\"receivedAt\":\"2026-01-21T12:00:00Z\"}}");
    }

    private MockResponse failedResponse() {
        return new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"success\":true,\"data\":{\"runId\":\"run-def\",\"jobId\":\"job-123\",\"status\":\"FAILED\",\"receivedAt\":\"2026-01-21T12:00:00Z\"}}");
    }

    // ========== 429 Retry-After Tests ==========

    /**
     * Verifies that 429 with Retry-After header (seconds) is respected.
     */
    @Test
    void shouldRespectRetryAfterSecondsHeader() throws InterruptedException {
        // First request: 429 with Retry-After: 1 (second)
        server.enqueue(new MockResponse()
                .setResponseCode(429)
                .addHeader("Retry-After", "1")
                .setBody("{\"success\":false,\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"Rate limit exceeded\"}}"));
        // Second request: success
        server.enqueue(successResponse());

        BearWatchConfig config = BearWatchConfig.builder("bw_test_api_key")
                .baseUrl(server.url("/").toString().replaceAll("/$", ""))
                .timeout(Duration.ofSeconds(5))
                .maxRetries(3)
                .retryDelay(Duration.ofMillis(10))  // Default is 10ms, but should use 1000ms from header
                .build();

        BearWatch client = BearWatch.create(config);

        try {
            long startTime = System.currentTimeMillis();
            var response = client.ping("job-123");
            long elapsed = System.currentTimeMillis() - startTime;

            assertThat(response.getRunId()).isEqualTo("run-abc");
            // Should have waited approximately 1 second (Retry-After: 1)
            // Allow some tolerance for test execution time
            assertThat(elapsed).isGreaterThanOrEqualTo(900L);
        } finally {
            client.close();
        }
    }

    /**
     * Verifies that 429 without Retry-After uses default backoff.
     */
    @Test
    void shouldUseDefaultBackoffWithoutRetryAfterHeader() throws InterruptedException {
        // First request: 429 without Retry-After
        server.enqueue(new MockResponse()
                .setResponseCode(429)
                .setBody("{\"success\":false,\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"Rate limit exceeded\"}}"));
        // Second request: success
        server.enqueue(successResponse());

        BearWatchConfig config = BearWatchConfig.builder("bw_test_api_key")
                .baseUrl(server.url("/").toString().replaceAll("/$", ""))
                .timeout(Duration.ofSeconds(5))
                .maxRetries(3)
                .retryDelay(Duration.ofMillis(50))
                .build();

        BearWatch client = BearWatch.create(config);

        try {
            long startTime = System.currentTimeMillis();
            var response = client.ping("job-123");
            long elapsed = System.currentTimeMillis() - startTime;

            assertThat(response.getRunId()).isEqualTo("run-abc");
            // Should use default backoff (much less than 1 second)
            assertThat(elapsed).isLessThan(500L);
        } finally {
            client.close();
        }
    }

    /**
     * Verifies that BearWatchException contains Retry-After information and response body.
     */
    @Test
    void rateLimitedExceptionShouldContainRetryAfterMsAndResponseBody() throws InterruptedException {
        String responseBody = "{\"success\":false,\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"Rate limit exceeded\"}}";

        server.enqueue(new MockResponse()
                .setResponseCode(429)
                .addHeader("Retry-After", "120")
                .addHeader("Content-Type", "application/json")
                .setBody(responseBody));

        BearWatchConfig config = BearWatchConfig.builder("bw_test_api_key")
                .baseUrl(server.url("/").toString().replaceAll("/$", ""))
                .timeout(Duration.ofSeconds(2))
                .maxRetries(0)  // No retry
                .build();

        BearWatch client = BearWatch.create(config);

        try {
            assertThatThrownBy(() -> client.ping("job-123"))
                    .isInstanceOf(BearWatchException.class)
                    .satisfies(e -> {
                        BearWatchException ex = (BearWatchException) e;
                        assertThat(ex.getStatusCode()).isEqualTo(429);
                        assertThat(ex.getRetryAfterMs()).isEqualTo(120_000L);  // 120 seconds in ms
                        assertThat(ex.getResponseBody()).isEqualTo(responseBody);
                    });
        } finally {
            client.close();
        }
    }
}
