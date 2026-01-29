package io.bearwatch.sdk;

import io.bearwatch.sdk.model.HeartbeatResponse;
import io.bearwatch.sdk.model.RequestStatus;
import io.bearwatch.sdk.model.Status;
import io.bearwatch.sdk.options.PingOptions;
import io.bearwatch.sdk.options.WrapOptions;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("deprecation")
class BearWatchTest {

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
    void shouldPingSuccessfully() throws InterruptedException {
        server.enqueue(successResponse());

        HeartbeatResponse response = client.ping("job-123");

        assertThat(response.getRunId()).isEqualTo("run-abc");
        assertThat(response.getJobId()).isEqualTo("job-123");
        assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/v1/ingest/jobs/job-123/heartbeat");
        assertThat(request.getHeader("X-API-Key")).isEqualTo("bw_test_api_key");
        assertThat(request.getHeader("Content-Type")).contains("application/json");
    }

    @Test
    void shouldPingWithOptions() throws InterruptedException {
        server.enqueue(successResponse());

        client.ping("job-123", PingOptions.builder()
                .status(RequestStatus.SUCCESS)
                .output("Processed 100 records")
                .metadata("recordCount", 100)
                .build());

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"status\":\"SUCCESS\"");
        assertThat(body).contains("\"output\":\"Processed 100 records\"");
        assertThat(body).contains("\"recordCount\":100");
        assertThat(body).contains("\"startedAt\"");
        assertThat(body).contains("\"completedAt\"");
    }

    @Test
    void shouldPingWithStatus() throws InterruptedException {
        server.enqueue(failedResponse());

        client.ping("job-123", RequestStatus.FAILED);

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"status\":\"FAILED\"");
    }

    @Test
    void shouldWrapSuccessfulTask() throws InterruptedException {
        server.enqueue(successResponse());

        AtomicReference<String> result = new AtomicReference<>();

        client.wrap("job-123", () -> {
            result.set("executed");
        });

        assertThat(result.get()).isEqualTo("executed");

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"status\":\"SUCCESS\"");
        assertThat(body).contains("\"startedAt\":");
        assertThat(body).contains("\"completedAt\":");
    }

    @Test
    void shouldWrapTaskAndReturnResult() throws InterruptedException {
        server.enqueue(successResponse());

        Integer result = client.wrap("job-123", () -> 42);

        assertThat(result).isEqualTo(42);

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"status\":\"SUCCESS\"");
    }

    @Test
    void shouldWrapFailedTask() {
        server.enqueue(failedResponse());

        assertThatThrownBy(() -> client.wrap("job-123", () -> {
            throw new RuntimeException("Task failed!");
        }))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Task failed!");
    }

    @Test
    void shouldWrapWithOptions() throws InterruptedException {
        server.enqueue(successResponse());

        WrapOptions options = WrapOptions.builder()
                .output("Processed 100 records")
                .metadata("recordCount", 100)
                .build();

        client.wrap("job-123", options, () -> {
            // task
        });

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"status\":\"SUCCESS\"");
        assertThat(body).contains("\"output\":\"Processed 100 records\"");
        assertThat(body).contains("\"recordCount\":100");
    }

    @Test
    void shouldWrapWithOptionsAndReturnResult() throws InterruptedException {
        server.enqueue(successResponse());

        WrapOptions options = WrapOptions.builder()
                .output("Result computed")
                .metadata("value", 42)
                .build();

        Integer result = client.wrap("job-123", options, () -> 42);

        assertThat(result).isEqualTo(42);

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"output\":\"Result computed\"");
        assertThat(body).contains("\"value\":42");
    }

    @Test
    void shouldWrapWithOptionsOnFailure() throws InterruptedException {
        server.enqueue(failedResponse());

        WrapOptions options = WrapOptions.builder()
                .metadata("attemptCount", 3)
                .build();

        assertThatThrownBy(() -> client.wrap("job-123", options, () -> {
            throw new RuntimeException("Task failed!");
        }))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Task failed!");

        // Verify metadata is sent even on failure
        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"status\":\"FAILED\"");
        assertThat(body).contains("\"attemptCount\":3");
    }

    // ========== wrapAsync Tests ==========

    @Test
    void shouldWrapAsyncSuccessfulTask() throws Exception {
        server.enqueue(successResponse());

        String result = client.wrapAsync("job-123", () ->
                CompletableFuture.completedFuture("async result")
        ).get();

        assertThat(result).isEqualTo("async result");

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"status\":\"SUCCESS\"");
        assertThat(body).contains("\"startedAt\":");
        assertThat(body).contains("\"completedAt\":");
    }

    @Test
    void shouldWrapAsyncFailedTask() throws InterruptedException {
        server.enqueue(failedResponse());

        CompletableFuture<String> future = client.wrapAsync("job-123", () ->
                CompletableFuture.<String>failedFuture(new RuntimeException("Async task failed!"))
        );

        assertThatThrownBy(() -> future.get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("Async task failed!");

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"status\":\"FAILED\"");
        assertThat(body).contains("\"error\":\"Async task failed!\"");
    }

    @Test
    void shouldWrapAsyncReturnResultEvenWhenHeartbeatFails() throws Exception {
        // Heartbeat fails with 500
        server.enqueue(new MockResponse().setResponseCode(500).setBody("Server Error"));

        String result = client.wrapAsync("job-123", () ->
                CompletableFuture.completedFuture("success despite heartbeat failure")
        ).get();

        // Original result should be returned despite heartbeat failure
        assertThat(result).isEqualTo("success despite heartbeat failure");
    }

    @Test
    void shouldWrapAsyncRethrowOriginalErrorEvenWhenHeartbeatFails() {
        // Heartbeat fails with 500
        server.enqueue(new MockResponse().setResponseCode(500).setBody("Server Error"));

        CompletableFuture<String> future = client.wrapAsync("job-123", () ->
                CompletableFuture.<String>failedFuture(new RuntimeException("Original error"))
        );

        // Original error should be thrown despite heartbeat failure
        assertThatThrownBy(() -> future.get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("Original error");
    }

    @Test
    void shouldWrapAsyncWithOptions() throws Exception {
        server.enqueue(successResponse());

        WrapOptions options = WrapOptions.builder()
                .output("Async processed")
                .metadata("async", true)
                .build();

        String result = client.wrapAsync("job-123", options, () ->
                CompletableFuture.completedFuture("async result")
        ).get();

        assertThat(result).isEqualTo("async result");

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"output\":\"Async processed\"");
        assertThat(body).contains("\"async\":true");
    }

    @Test
    void shouldThrowOnInvalidApiKey() {
        server.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("{\"success\":false,\"error\":{\"code\":\"INVALID_API_KEY\",\"message\":\"Invalid API key\"}}"));

        assertThatThrownBy(() -> client.ping("job-123"))
                .isInstanceOf(BearWatchException.class)
                .satisfies(e -> {
                    BearWatchException ex = (BearWatchException) e;
                    assertThat(ex.getStatusCode()).isEqualTo(401);
                });
    }

    @Test
    void shouldThrowOnServerError() {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        assertThatThrownBy(() -> client.ping("job-123"))
                .isInstanceOf(BearWatchException.class)
                .satisfies(e -> {
                    BearWatchException ex = (BearWatchException) e;
                    assertThat(ex.getStatusCode()).isEqualTo(500);
                });
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
}
