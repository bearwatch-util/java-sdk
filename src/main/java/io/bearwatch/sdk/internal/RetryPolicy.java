package io.bearwatch.sdk.internal;

import io.bearwatch.sdk.BearWatchException;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Retry policy with exponential backoff and jitter.
 */
public class RetryPolicy {

    private final int maxRetries;
    private final Duration baseDelay;

    public RetryPolicy(int maxRetries, Duration baseDelay) {
        this.maxRetries = maxRetries;
        this.baseDelay = baseDelay;
    }

    /**
     * Executes an operation with retry logic.
     *
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     * @throws BearWatchException if all retries are exhausted
     */
    public <T> T execute(Supplier<T> operation) {
        int attempt = 0;
        BearWatchException lastException = null;

        while (attempt <= maxRetries) {
            try {
                return operation.get();
            } catch (BearWatchException e) {
                lastException = e;

                if (!isRetryable(e) || attempt >= maxRetries) {
                    throw e;
                }

                attempt++;
                sleep(calculateDelay(attempt, e));
            }
        }

        throw lastException != null ? lastException : new BearWatchException("Max retries exceeded");
    }

    /**
     * Determines if an exception is retryable.
     */
    private boolean isRetryable(BearWatchException e) {
        int statusCode = e.getStatusCode();

        // Retry on server errors (5xx)
        if (statusCode >= 500 && statusCode < 600) {
            return true;
        }

        // Retry on rate limiting (429)
        if (statusCode == 429) {
            return true;
        }

        // Retry on network errors (IOException in cause chain)
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof IOException) {
                return true;
            }
            cause = cause.getCause();
        }

        return false;
    }

    /**
     * Calculates delay with exponential backoff and jitter.
     * Respects Retry-After header for 429 responses.
     */
    private long calculateDelay(int attempt, BearWatchException e) {
        // For 429 with Retry-After, use the server-specified delay
        if (e.getStatusCode() == 429 && e.getRetryAfterMs() != null) {
            return e.getRetryAfterMs();
        }

        // Default: exponential backoff with jitter
        // Exponential backoff: baseDelay * 2^attempt
        long exponentialDelay = baseDelay.toMillis() * (1L << attempt);

        // Add jitter: 50% to 100% of the delay
        double jitter = 0.5 + ThreadLocalRandom.current().nextDouble() * 0.5;

        return (long) (exponentialDelay * jitter);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sneakyThrow(e);  // InterruptedException 원본 그대로 전파
        }
    }

    /**
     * Throws a checked exception without declaring it in the method signature.
     * This allows InterruptedException to propagate naturally to callers who can catch it.
     */
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }
}
