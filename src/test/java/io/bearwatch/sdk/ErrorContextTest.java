package io.bearwatch.sdk;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for ErrorContext in BearWatchException.
 */
class ErrorContextTest {

    private MockWebServer server;
    private BearWatch client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        BearWatchConfig config = BearWatchConfig.builder("bw_test_api_key")
                .baseUrl(server.url("/").toString().replaceAll("/$", ""))
                .timeout(Duration.ofSeconds(5))
                .maxRetries(0)
                .build();

        client = BearWatch.create(config);
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
        server.shutdown();
    }

    @Test
    void pingExceptionShouldContainContext() {
        server.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody("{\"success\":false,\"error\":{\"code\":\"JOB_NOT_FOUND\",\"message\":\"Job not found\"}}"));

        assertThatThrownBy(() -> client.ping("my-job-123"))
                .isInstanceOf(BearWatchException.class)
                .satisfies(e -> {
                    BearWatchException ex = (BearWatchException) e;
                    assertThat(ex.getContext()).isNotNull();
                    assertThat(ex.getContext().getJobId()).isEqualTo("my-job-123");
                    assertThat(ex.getContext().getOperation()).isEqualTo("ping");
                    assertThat(ex.getContext().getRunId()).isNull();
                });
    }

    @Test
    void pingAsyncExceptionShouldContainContext() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<BearWatchException> capturedError = new AtomicReference<>();

        client.pingAsync("async-job", new io.bearwatch.sdk.callback.ResultCallback<>() {
            @Override
            public void onSuccess(io.bearwatch.sdk.model.HeartbeatResponse result) {
                latch.countDown();
            }

            @Override
            public void onFailure(BearWatchException error) {
                capturedError.set(error);
                latch.countDown();
            }
        });

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(capturedError.get()).isNotNull();
        assertThat(capturedError.get().getContext()).isNotNull();
        assertThat(capturedError.get().getContext().getJobId()).isEqualTo("async-job");
        assertThat(capturedError.get().getContext().getOperation()).isEqualTo("pingAsync");
    }

    @Test
    void errorContextToStringShouldBeReadable() {
        BearWatchException.ErrorContext context =
                new BearWatchException.ErrorContext("job-123", "run-456", "ping");

        String str = context.toString();
        assertThat(str).contains("job-123");
        assertThat(str).contains("run-456");
        assertThat(str).contains("ping");
    }

    @Test
    void withContextShouldPreserveAllFields() {
        // Create an exception with all fields
        BearWatchException original = BearWatchException.rateLimited(5000L);

        // Add context
        BearWatchException.ErrorContext context =
                new BearWatchException.ErrorContext("job-id", "run-id", "ping");
        BearWatchException withContext = original.withContext(context);

        // Verify all fields are preserved
        assertThat(withContext.getStatusCode()).isEqualTo(429);
        assertThat(withContext.getErrorCode()).isEqualTo("RATE_LIMITED");
        assertThat(withContext.getMessage()).isEqualTo("Rate limit exceeded");
        assertThat(withContext.getRetryAfterMs()).isEqualTo(5000L);
        assertThat(withContext.getContext()).isEqualTo(context);
    }

    @Test
    void withContextShouldPreserveOriginalStackTraceAsSuppressed() {
        // Given: exception without cause (401, 404, 429 등)
        BearWatchException original = BearWatchException.invalidApiKey();

        // When: add context
        BearWatchException.ErrorContext context =
                new BearWatchException.ErrorContext("job-id", null, "ping");
        BearWatchException withContext = original.withContext(context);

        // Then: original is added as suppressed
        assertThat(withContext.getSuppressed()).hasSize(1);
        assertThat(withContext.getSuppressed()[0]).isSameAs(original);
        // Stack trace should be copied from original
        assertThat(withContext.getStackTrace()).isEqualTo(original.getStackTrace());
    }

    @Test
    void withContextShouldNotAddSuppressedWhenCauseExists() {
        // Given: exception with cause
        IOException networkCause = new IOException("Connection refused");
        BearWatchException original = BearWatchException.networkError(networkCause);

        // When: add context
        BearWatchException.ErrorContext context =
                new BearWatchException.ErrorContext("job-id", null, "ping");
        BearWatchException withContext = original.withContext(context);

        // Then: suppressed is empty (cause chain is sufficient)
        assertThat(withContext.getSuppressed()).isEmpty();
        assertThat(withContext.getCause()).isSameAs(networkCause);
    }
}
