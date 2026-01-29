package io.bearwatch.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import io.bearwatch.sdk.callback.ResultCallback;
import io.bearwatch.sdk.internal.HttpClient;
import io.bearwatch.sdk.model.ApiResponse;
import io.bearwatch.sdk.model.HeartbeatRequest;
import io.bearwatch.sdk.model.HeartbeatResponse;
import io.bearwatch.sdk.model.RequestStatus;
import io.bearwatch.sdk.model.Status;
import io.bearwatch.sdk.options.PingOptions;
import io.bearwatch.sdk.options.WrapOptions;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Main client for the BearWatch SDK.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Create client
 * BearWatch bw = BearWatch.create(BearWatchConfig.builder("bw_your_api_key")
 *     .timeout(Duration.ofSeconds(30))
 *     .build());
 *
 * // Simple ping
 * bw.ping("my-job");
 *
 * // Wrap a task (automatic timing)
 * bw.wrap("my-job", () -> {
 *     // Your job logic
 * });
 *
 * // Manual timing
 * Instant startedAt = Instant.now();
 * // ... do work ...
 * bw.ping("my-job", PingOptions.builder()
 *     .startedAt(startedAt)
 *     .completedAt(Instant.now())
 *     .build());
 * }</pre>
 */
public class BearWatch {

    private static final TypeReference<ApiResponse<HeartbeatResponse>> HEARTBEAT_RESPONSE_TYPE =
            new TypeReference<>() {};

    private final BearWatchConfig config;
    private final HttpClient httpClient;

    private BearWatch(BearWatchConfig config) {
        this.config = config;
        this.httpClient = new HttpClient(config);
    }

    /**
     * Creates a new BearWatch client with the given configuration.
     *
     * @param config the configuration
     * @return a new BearWatch instance
     */
    public static BearWatch create(BearWatchConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        return new BearWatch(config);
    }

    // ========== Simple Ping ==========

    /**
     * Sends a success heartbeat for the given job.
     *
     * @param jobId the job ID
     * @return the heartbeat response
     */
    public HeartbeatResponse ping(String jobId) {
        return ping(jobId, (PingOptions) null);
    }

    /**
     * Sends a heartbeat with the given status.
     *
     * @param jobId the job ID
     * @param status the status
     * @return the heartbeat response
     */
    public HeartbeatResponse ping(String jobId, RequestStatus status) {
        return ping(jobId, PingOptions.builder().status(status).build());
    }

    /**
     * Sends a failed heartbeat with an error message.
     *
     * @param jobId the job ID
     * @param status the status (should be FAILED)
     * @param error the error message
     * @return the heartbeat response
     */
    public HeartbeatResponse ping(String jobId, RequestStatus status, String error) {
        return ping(jobId, PingOptions.builder().status(status).error(error).build());
    }

    /**
     * Sends a heartbeat with the given options.
     *
     * @param jobId the job ID
     * @param options the ping options
     * @return the heartbeat response
     */
    public HeartbeatResponse ping(String jobId, PingOptions options) {
        Objects.requireNonNull(jobId, "jobId must not be null");

        HeartbeatRequest request = options != null
                ? options.toRequest()
                : HeartbeatRequest.success();

        String path = "/api/v1/ingest/jobs/" + jobId + "/heartbeat";
        try {
            return httpClient.post(path, request, HEARTBEAT_RESPONSE_TYPE);
        } catch (BearWatchException e) {
            throw e.withContext(new BearWatchException.ErrorContext(jobId, null, "ping"));
        }
    }

    // ========== Async Ping ==========

    /**
     * Sends a success heartbeat asynchronously.
     *
     * @param jobId the job ID
     * @param callback the callback to invoke on completion
     */
    public void pingAsync(String jobId, ResultCallback<HeartbeatResponse> callback) {
        pingAsync(jobId, null, callback);
    }

    /**
     * Sends a heartbeat asynchronously with the given options.
     *
     * @param jobId the job ID
     * @param options the ping options
     * @param callback the callback to invoke on completion
     */
    public void pingAsync(String jobId, PingOptions options, ResultCallback<HeartbeatResponse> callback) {
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(callback, "callback must not be null");

        HeartbeatRequest request = options != null
                ? options.toRequest()
                : HeartbeatRequest.success();

        String path = "/api/v1/ingest/jobs/" + jobId + "/heartbeat";
        // Wrap callback to add context on failure
        ResultCallback<HeartbeatResponse> wrappedCallback = new ResultCallback<>() {
            @Override
            public void onSuccess(HeartbeatResponse result) {
                callback.onSuccess(result);
            }

            @Override
            public void onFailure(BearWatchException error) {
                callback.onFailure(error.withContext(
                        new BearWatchException.ErrorContext(jobId, null, "pingAsync")));
            }
        };
        httpClient.postAsync(path, request, HEARTBEAT_RESPONSE_TYPE, wrappedCallback);
    }

    // ========== Wrap Pattern ==========

    /**
     * Wraps a task with automatic heartbeat reporting.
     * Sends SUCCESS on completion, FAILED on exception.
     *
     * @param jobId the job ID
     * @param task the task to execute
     */
    public void wrap(String jobId, Runnable task) {
        wrap(jobId, null, task);
    }

    /**
     * Wraps a task with automatic heartbeat reporting and options.
     * Sends SUCCESS on completion, FAILED on exception.
     *
     * @param jobId the job ID
     * @param options the wrap options (output, metadata)
     * @param task the task to execute
     */
    public void wrap(String jobId, WrapOptions options, Runnable task) {
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(task, "task must not be null");

        String output = options != null ? options.getOutput() : null;
        Map<String, Object> metadata = options != null ? options.getMetadata() : null;

        Instant startedAt = Instant.now();
        try {
            task.run();
            completeInternal(jobId, startedAt, output, metadata);
        } catch (Exception e) {
            // Report failure but ignore heartbeat errors to preserve original exception
            try {
                String errorMessage = e.getMessage() != null ? e.getMessage() : e.toString();
                failInternal(jobId, startedAt, errorMessage, metadata);
            } catch (Exception ignored) {
                // Heartbeat failure is ignored - original exception takes priority
            }
            throw e;
        }
    }

    /**
     * Wraps a task with automatic heartbeat reporting and returns the result.
     * Sends SUCCESS on completion, FAILED on exception.
     *
     * <p>Note: If the task throws a checked exception, it will be wrapped in a
     * {@link BearWatchException}. This is a Java language constraint since checked
     * exceptions cannot be re-thrown directly. RuntimeExceptions are re-thrown as-is.</p>
     *
     * @param jobId the job ID
     * @param task the task to execute
     * @param <T> the result type
     * @return the task result
     * @throws RuntimeException if the task throws a RuntimeException
     * @throws BearWatchException if the task throws a checked exception (wraps the original)
     */
    public <T> T wrap(String jobId, Callable<T> task) {
        return wrap(jobId, null, task);
    }

    /**
     * Wraps a task with automatic heartbeat reporting, options, and returns the result.
     * Sends SUCCESS on completion, FAILED on exception.
     *
     * <p>Note: If the task throws a checked exception, it will be wrapped in a
     * {@link BearWatchException}. This is a Java language constraint since checked
     * exceptions cannot be re-thrown directly. RuntimeExceptions are re-thrown as-is.</p>
     *
     * @param jobId the job ID
     * @param options the wrap options (output, metadata)
     * @param task the task to execute
     * @param <T> the result type
     * @return the task result
     * @throws RuntimeException if the task throws a RuntimeException
     * @throws BearWatchException if the task throws a checked exception (wraps the original)
     */
    public <T> T wrap(String jobId, WrapOptions options, Callable<T> task) {
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(task, "task must not be null");

        String output = options != null ? options.getOutput() : null;
        Map<String, Object> metadata = options != null ? options.getMetadata() : null;

        Instant startedAt = Instant.now();
        try {
            T result = task.call();
            completeInternal(jobId, startedAt, output, metadata);
            return result;
        } catch (Exception e) {
            // Report failure but ignore heartbeat errors to preserve original exception
            try {
                String errorMessage = e.getMessage() != null ? e.getMessage() : e.toString();
                failInternal(jobId, startedAt, errorMessage, metadata);
            } catch (Exception ignored) {
                // Heartbeat failure is ignored - original exception takes priority
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new BearWatchException("Task failed", e);
        }
    }

    /**
     * Wraps an async task with automatic heartbeat reporting.
     * Sends SUCCESS on completion, FAILED on exception.
     * Waits for heartbeat to complete before returning, but ignores heartbeat errors.
     *
     * @param jobId the job ID
     * @param task the async task to execute (returns a CompletableFuture)
     * @param <T> the result type
     * @return a CompletableFuture that completes with the task result
     */
    public <T> CompletableFuture<T> wrapAsync(String jobId, Supplier<CompletableFuture<T>> task) {
        return wrapAsync(jobId, null, task);
    }

    /**
     * Wraps an async task with automatic heartbeat reporting and options.
     * Sends SUCCESS on completion, FAILED on exception.
     * Waits for heartbeat to complete before returning, but ignores heartbeat errors.
     *
     * @param jobId the job ID
     * @param options the wrap options (output, metadata)
     * @param task the async task to execute (returns a CompletableFuture)
     * @param <T> the result type
     * @return a CompletableFuture that completes with the task result
     */
    public <T> CompletableFuture<T> wrapAsync(String jobId, WrapOptions options, Supplier<CompletableFuture<T>> task) {
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(task, "task must not be null");

        String output = options != null ? options.getOutput() : null;
        Map<String, Object> metadata = options != null ? options.getMetadata() : null;

        Instant startedAt = Instant.now();
        return task.get()
                .<CompletableFuture<T>>handle((result, error) -> {
                    if (error != null) {
                        // Send failure heartbeat and wait for it, then re-throw original error
                        Throwable cause = error.getCause() != null ? error.getCause() : error;
                        String errorMessage = cause.getMessage() != null ? cause.getMessage() : cause.toString();
                        return failInternalAsync(jobId, startedAt, errorMessage, metadata)
                                .<T>handle((heartbeatResponse, heartbeatError) -> {
                                    // Ignore heartbeat error, re-throw original error
                                    if (error instanceof RuntimeException) {
                                        throw (RuntimeException) error;
                                    }
                                    throw new CompletionException(error);
                                });
                    } else {
                        // Wait for heartbeat to complete, but ignore errors and return original result
                        return completeInternalAsync(jobId, startedAt, output, metadata)
                                .handle((heartbeatResponse, heartbeatError) -> result);
                    }
                })
                .thenCompose(Function.identity());
    }

    // ========== Internal Methods ==========

    HeartbeatResponse completeInternal(String jobId, Instant startedAt, String output, Map<String, Object> metadata) {
        Instant completedAt = Instant.now();
        Instant effectiveStartedAt = startedAt != null ? startedAt : completedAt;

        HeartbeatRequest request = new HeartbeatRequest(
                RequestStatus.SUCCESS,
                effectiveStartedAt,
                completedAt,
                output,
                null,
                metadata
        );

        String path = "/api/v1/ingest/jobs/" + jobId + "/heartbeat";
        try {
            return httpClient.post(path, request, HEARTBEAT_RESPONSE_TYPE);
        } catch (BearWatchException e) {
            throw e.withContext(new BearWatchException.ErrorContext(jobId, null, "complete"));
        }
    }

    HeartbeatResponse failInternal(String jobId, Instant startedAt, String error, Map<String, Object> metadata) {
        Instant completedAt = Instant.now();
        Instant effectiveStartedAt = startedAt != null ? startedAt : completedAt;

        HeartbeatRequest request = new HeartbeatRequest(
                RequestStatus.FAILED,
                effectiveStartedAt,
                completedAt,
                null,
                error,
                metadata
        );

        String path = "/api/v1/ingest/jobs/" + jobId + "/heartbeat";
        try {
            return httpClient.post(path, request, HEARTBEAT_RESPONSE_TYPE);
        } catch (BearWatchException e) {
            throw e.withContext(new BearWatchException.ErrorContext(jobId, null, "fail"));
        }
    }

    CompletableFuture<HeartbeatResponse> completeInternalAsync(String jobId, Instant startedAt, String output, Map<String, Object> metadata) {
        Instant completedAt = Instant.now();
        Instant effectiveStartedAt = startedAt != null ? startedAt : completedAt;

        HeartbeatRequest request = new HeartbeatRequest(
                RequestStatus.SUCCESS,
                effectiveStartedAt,
                completedAt,
                output,
                null,
                metadata
        );

        String path = "/api/v1/ingest/jobs/" + jobId + "/heartbeat";
        return httpClient.postFuture(path, request, HEARTBEAT_RESPONSE_TYPE)
                .exceptionally(e -> {
                    // Check if e itself is BearWatchException
                    if (e instanceof BearWatchException) {
                        throw ((BearWatchException) e).withContext(new BearWatchException.ErrorContext(jobId, null, "completeAsync"));
                    }
                    // Check if cause is BearWatchException (wrapped in CompletionException)
                    Throwable cause = e.getCause();
                    if (cause instanceof BearWatchException) {
                        throw ((BearWatchException) cause).withContext(new BearWatchException.ErrorContext(jobId, null, "completeAsync"));
                    }
                    throw new BearWatchException("Unexpected error", e);
                });
    }

    CompletableFuture<HeartbeatResponse> failInternalAsync(String jobId, Instant startedAt, String error, Map<String, Object> metadata) {
        Instant completedAt = Instant.now();
        Instant effectiveStartedAt = startedAt != null ? startedAt : completedAt;

        HeartbeatRequest request = new HeartbeatRequest(
                RequestStatus.FAILED,
                effectiveStartedAt,
                completedAt,
                null,
                error,
                metadata
        );

        String path = "/api/v1/ingest/jobs/" + jobId + "/heartbeat";
        return httpClient.postFuture(path, request, HEARTBEAT_RESPONSE_TYPE)
                .exceptionally(e -> {
                    // Check if e itself is BearWatchException
                    if (e instanceof BearWatchException) {
                        throw ((BearWatchException) e).withContext(new BearWatchException.ErrorContext(jobId, null, "failAsync"));
                    }
                    // Check if cause is BearWatchException (wrapped in CompletionException)
                    Throwable cause = e.getCause();
                    if (cause instanceof BearWatchException) {
                        throw ((BearWatchException) cause).withContext(new BearWatchException.ErrorContext(jobId, null, "failAsync"));
                    }
                    throw new BearWatchException("Unexpected error", e);
                });
    }

    // ========== Lifecycle ==========

    /**
     * Closes the client and releases resources.
     */
    public void close() {
        httpClient.close();
    }
}
