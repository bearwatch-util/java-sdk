package io.bearwatch.sdk;

/**
 * Exception thrown by BearWatch SDK operations.
 */
public class BearWatchException extends RuntimeException {

    private final int statusCode;
    private final String errorCode;
    private final String responseBody;
    private final Long retryAfterMs;
    private final ErrorContext context;

    /**
     * Context information about the operation that failed.
     * Useful for debugging and logging.
     */
    public static class ErrorContext {
        private final String jobId;
        private final String runId;
        private final String operation;

        public ErrorContext(String jobId, String runId, String operation) {
            this.jobId = jobId;
            this.runId = runId;
            this.operation = operation;
        }

        /**
         * Returns the job ID associated with this error, or null if not applicable.
         */
        public String getJobId() {
            return jobId;
        }

        /**
         * Returns the run ID associated with this error, or null if not applicable.
         */
        public String getRunId() {
            return runId;
        }

        /**
         * Returns the operation that failed (e.g., "ping", "start", "complete", "fail").
         */
        public String getOperation() {
            return operation;
        }

        @Override
        public String toString() {
            return "ErrorContext{" +
                    "jobId='" + jobId + '\'' +
                    ", runId='" + runId + '\'' +
                    ", operation='" + operation + '\'' +
                    '}';
        }
    }

    public BearWatchException(String message) {
        super(message);
        this.statusCode = 0;
        this.errorCode = null;
        this.responseBody = null;
        this.retryAfterMs = null;
        this.context = null;
    }

    public BearWatchException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.errorCode = null;
        this.responseBody = null;
        this.retryAfterMs = null;
        this.context = null;
    }

    public BearWatchException(int statusCode, String errorCode, String message, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.responseBody = responseBody;
        this.retryAfterMs = null;
        this.context = null;
    }

    public BearWatchException(int statusCode, String errorCode, String message, String responseBody, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.responseBody = responseBody;
        this.retryAfterMs = null;
        this.context = null;
    }

    public BearWatchException(int statusCode, String errorCode, String message, String responseBody, Long retryAfterMs) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.responseBody = responseBody;
        this.retryAfterMs = retryAfterMs;
        this.context = null;
    }

    // Private constructor with all fields for withContext()
    private BearWatchException(int statusCode, String errorCode, String message, String responseBody,
                               Long retryAfterMs, Throwable cause, ErrorContext context) {
        super(message, cause);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.responseBody = responseBody;
        this.retryAfterMs = retryAfterMs;
        this.context = context;
    }

    /**
     * Returns the HTTP status code, or 0 if not applicable.
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Returns the BearWatch error code, or null if not applicable.
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Returns the raw response body, or null if not applicable.
     */
    public String getResponseBody() {
        return responseBody;
    }

    /**
     * Returns the Retry-After delay in milliseconds, or null if not applicable.
     * This is set when a 429 response includes a Retry-After header.
     */
    public Long getRetryAfterMs() {
        return retryAfterMs;
    }

    /**
     * Returns the context information about the failed operation, or null if not set.
     */
    public ErrorContext getContext() {
        return context;
    }

    /**
     * Returns a new exception with the given context attached.
     * This is used internally by the SDK to add context information to exceptions.
     * <p>
     * When the original exception has no cause, it is added as a suppressed exception
     * to preserve its stack trace for debugging purposes.
     *
     * @param context the error context
     * @return a new exception with the context attached
     */
    public BearWatchException withContext(ErrorContext context) {
        BearWatchException newException = new BearWatchException(
                this.statusCode,
                this.errorCode,
                this.getMessage(),
                this.responseBody,
                this.retryAfterMs,
                this.getCause(),
                context
        );
        newException.setStackTrace(this.getStackTrace());
        if (this.getCause() == null) {
            newException.addSuppressed(this);
        }
        return newException;
    }

    /**
     * Creates an exception for network errors.
     */
    public static BearWatchException networkError(Throwable cause) {
        return new BearWatchException(0, "NETWORK_ERROR",
                "Network error: " + cause.getMessage(), null, cause);
    }

    /**
     * Creates an exception for timeout errors.
     */
    public static BearWatchException timeout() {
        return new BearWatchException(0, "TIMEOUT", "Request timed out", null);
    }

    /**
     * Creates an exception for invalid API key errors.
     */
    public static BearWatchException invalidApiKey() {
        return new BearWatchException(401, "INVALID_API_KEY", "Invalid or expired API key", null);
    }

    /**
     * Creates an exception for job not found errors.
     */
    public static BearWatchException jobNotFound(String jobId) {
        return new BearWatchException(404, "JOB_NOT_FOUND", "Job not found: " + jobId, null);
    }

    /**
     * Creates an exception for rate limit errors.
     */
    public static BearWatchException rateLimited() {
        return new BearWatchException(429, "RATE_LIMITED", "Rate limit exceeded", null, (Long) null);
    }

    /**
     * Creates an exception for rate limit errors with Retry-After.
     *
     * @param retryAfterMs the retry delay in milliseconds from Retry-After header
     */
    public static BearWatchException rateLimited(Long retryAfterMs) {
        return new BearWatchException(429, "RATE_LIMITED", "Rate limit exceeded", null, retryAfterMs);
    }

    /**
     * Creates an exception for rate limit errors with Retry-After and response body.
     *
     * @param retryAfterMs the retry delay in milliseconds from Retry-After header
     * @param responseBody the raw response body for diagnostics
     */
    public static BearWatchException rateLimited(Long retryAfterMs, String responseBody) {
        return new BearWatchException(429, "RATE_LIMITED", "Rate limit exceeded", responseBody, retryAfterMs);
    }

    /**
     * Creates an exception for server errors.
     */
    public static BearWatchException serverError(int statusCode, String responseBody) {
        return new BearWatchException(statusCode, "SERVER_ERROR", "Server error: " + statusCode, responseBody);
    }

    /**
     * Creates an exception for invalid/malformed responses.
     */
    public static BearWatchException invalidResponse(String message, int statusCode, String responseBody) {
        return new BearWatchException(statusCode, "INVALID_RESPONSE", message, responseBody);
    }

    /**
     * Creates an exception for invalid/malformed responses with cause.
     */
    public static BearWatchException invalidResponse(String message, int statusCode, String responseBody, Throwable cause) {
        return new BearWatchException(statusCode, "INVALID_RESPONSE", message, responseBody, cause);
    }
}
