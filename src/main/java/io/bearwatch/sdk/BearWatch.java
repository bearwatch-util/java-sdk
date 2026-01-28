package io.bearwatch.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import io.bearwatch.sdk.callback.ResultCallback;
import io.bearwatch.sdk.internal.HttpClient;
import io.bearwatch.sdk.model.ApiResponse;
import io.bearwatch.sdk.model.HeartbeatRequest;
import io.bearwatch.sdk.model.HeartbeatResponse;
import io.bearwatch.sdk.model.RequestStatus;
import io.bearwatch.sdk.model.Status;
import io.bearwatch.sdk.options.CompleteOptions;
import io.bearwatch.sdk.options.PingOptions;

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
     * Sends a heartbeat with the given status.
     *
     * @param jobId the job ID
     * @param status the status
     * @return the heartbeat response
     * @throws IllegalArgumentException if status is TIMEOUT or MISSED
     * @deprecated Use {@link #ping(String, RequestStatus)} instead.
     *             TIMEOUT and MISSED are server-detected states and cannot be set in requests.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public HeartbeatResponse ping(String jobId, Status status) {
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
     * Sends a failed heartbeat with an error message.
     *
     * @param jobId the job ID
     * @param status the status (should be FAILED)
     * @param error the error message
     * @return the heartbeat response
     * @throws IllegalArgumentException if status is TIMEOUT or MISSED
     * @deprecated Use {@link #ping(String, RequestStatus, String)} instead.
     *             TIMEOUT and MISSED are server-detected states and cannot be set in requests.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public HeartbeatResponse ping(String jobId, Status status, String error) {
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
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(task, "task must not be null");

        Instant startedAt = Instant.now();
        try {
            task.run();
            completeInternal(jobId, startedAt, null, null);
        } catch (Exception e) {
            // Report failure but ignore heartbeat errors to preserve original exception
            try {
                String errorMessage = e.getMessage() != null ? e.getMessage() : e.toString();
                failInternal(jobId, startedAt, errorMessage);
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
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(task, "task must not be null");

        Instant startedAt = Instant.now();
        try {
            T result = task.call();
            completeInternal(jobId, startedAt, null, null);
            return result;
        } catch (Exception e) {
            // Report failure but ignore heartbeat errors to preserve original exception
            try {
                String errorMessage = e.getMessage() != null ? e.getMessage() : e.toString();
                failInternal(jobId, startedAt, errorMessage);
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
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(task, "task must not be null");

        Instant startedAt = Instant.now();
        return task.get()
                .<CompletableFuture<T>>handle((result, error) -> {
                    if (error != null) {
                        // Send failure heartbeat and wait for it, then re-throw original error
                        Throwable cause = error.getCause() != null ? error.getCause() : error;
                        String errorMessage = cause.getMessage() != null ? cause.getMessage() : cause.toString();
                        return failInternalAsync(jobId, startedAt, errorMessage)
                                .<T>handle((heartbeatResponse, heartbeatError) -> {
                                    // Ignore heartbeat error, re-throw original error
                                    if (error instanceof RuntimeException) {
                                        throw (RuntimeException) error;
                                    }
                                    throw new CompletionException(error);
                                });
                    } else {
                        // Wait for heartbeat to complete, but ignore errors and return original result
                        return completeInternalAsync(jobId, startedAt, null, null)
                                .handle((heartbeatResponse, heartbeatError) -> result);
                    }
                })
                .thenCompose(Function.identity());
    }

    // ========== Complete/Fail ==========

    /**
     * Sends a success completion for a job.
     *
     * @param jobId the job ID
     * @return the heartbeat response
     */
    public HeartbeatResponse complete(String jobId) {
        return complete(jobId, null);
    }

    /**
     * Sends a success completion for a job with options.
     *
     * @param jobId the job ID
     * @param options the complete options
     * @return the heartbeat response
     */
    public HeartbeatResponse complete(String jobId, CompleteOptions options) {
        return completeInternal(
                jobId,
                null,
                options != null ? options.getOutput() : null,
                options != null ? options.getMetadata() : null
        );
    }

    /**
     * Sends a failure for a job.
     *
     * @param jobId the job ID
     * @param error the error message
     * @return the heartbeat response
     */
    public HeartbeatResponse fail(String jobId, String error) {
        return failInternal(jobId, null, error);
    }

    /**
     * Sends a failure for a job with an exception.
     *
     * @param jobId the job ID
     * @param error the exception
     * @return the heartbeat response
     */
    public HeartbeatResponse fail(String jobId, Throwable error) {
        return fail(jobId, error.getMessage());
    }

    // ========== Async Complete/Fail ==========

    /**
     * Sends a success completion for a job asynchronously.
     *
     * @param jobId the job ID
     * @return a CompletableFuture that completes with the heartbeat response
     */
    public CompletableFuture<HeartbeatResponse> completeAsync(String jobId) {
        return completeAsync(jobId, null);
    }

    /**
     * Sends a success completion for a job asynchronously with options.
     *
     * @param jobId the job ID
     * @param options the complete options
     * @return a CompletableFuture that completes with the heartbeat response
     */
    public CompletableFuture<HeartbeatResponse> completeAsync(String jobId, CompleteOptions options) {
        return completeInternalAsync(
                jobId,
                null,
                options != null ? options.getOutput() : null,
                options != null ? options.getMetadata() : null
        );
    }

    /**
     * Sends a failure for a job asynchronously.
     *
     * @param jobId the job ID
     * @param error the error message
     * @return a CompletableFuture that completes with the heartbeat response
     */
    public CompletableFuture<HeartbeatResponse> failAsync(String jobId, String error) {
        return failInternalAsync(jobId, null, error);
    }

    /**
     * Sends a failure for a job asynchronously with an exception.
     *
     * @param jobId the job ID
     * @param error the exception
     * @return a CompletableFuture that completes with the heartbeat response
     */
    public CompletableFuture<HeartbeatResponse> failAsync(String jobId, Throwable error) {
        return failAsync(jobId, error.getMessage());
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

    HeartbeatResponse failInternal(String jobId, Instant startedAt, String error) {
        Instant completedAt = Instant.now();
        Instant effectiveStartedAt = startedAt != null ? startedAt : completedAt;

        HeartbeatRequest request = new HeartbeatRequest(
                RequestStatus.FAILED,
                effectiveStartedAt,
                completedAt,
                null,
                error,
                null
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

    CompletableFuture<HeartbeatResponse> failInternalAsync(String jobId, Instant startedAt, String error) {
        Instant completedAt = Instant.now();
        Instant effectiveStartedAt = startedAt != null ? startedAt : completedAt;

        HeartbeatRequest request = new HeartbeatRequest(
                RequestStatus.FAILED,
                effectiveStartedAt,
                completedAt,
                null,
                error,
                null
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
