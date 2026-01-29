package io.bearwatch.sdk.options;

import io.bearwatch.sdk.model.HeartbeatRequest;
import io.bearwatch.sdk.model.RequestStatus;
import io.bearwatch.sdk.model.Status;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Options for the ping() operation.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * PingOptions options = PingOptions.builder()
 *     .status(RequestStatus.SUCCESS)
 *     .startedAt(startTime)
 *     .completedAt(endTime)
 *     .output("Processed 100 records")
 *     .metadata("recordCount", 100)
 *     .build();
 * }</pre>
 */
public class PingOptions {

    private final RequestStatus status;
    private final Instant startedAt;
    private final Instant completedAt;
    private final String output;
    private final String error;
    private final Map<String, Object> metadata;

    private PingOptions(Builder builder) {
        this.status = builder.status;
        this.startedAt = builder.startedAt;
        this.completedAt = builder.completedAt;
        this.output = builder.output;
        this.error = builder.error;
        this.metadata = builder.metadata.isEmpty() ? null : new HashMap<>(builder.metadata);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the job status.
     *
     * @return the status
     */
    public RequestStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getOutput() {
        return output;
    }

    public String getError() {
        return error;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Converts this options object to a HeartbeatRequest.
     */
    public HeartbeatRequest toRequest() {
        RequestStatus effectiveStatus = status != null ? status : RequestStatus.SUCCESS;
        Instant now = Instant.now();
        Instant effectiveCompletedAt = completedAt != null ? completedAt : now;
        Instant effectiveStartedAt = startedAt != null ? startedAt : effectiveCompletedAt;

        return new HeartbeatRequest(
                effectiveStatus,
                effectiveStartedAt,
                effectiveCompletedAt,
                output,
                error,
                metadata
        );
    }

    public static class Builder {
        private RequestStatus status = RequestStatus.SUCCESS;
        private Instant startedAt;
        private Instant completedAt;
        private String output;
        private String error;
        private final Map<String, Object> metadata = new HashMap<>();

        private Builder() {}

        /**
         * Sets the job status.
         *
         * @param status the status (default: SUCCESS). Only RUNNING, SUCCESS, FAILED are valid.
         * @return this builder
         */
        public Builder status(RequestStatus status) {
            this.status = status;
            return this;
        }

        /**
         * Sets when the job started.
         *
         * @param startedAt the start time
         * @return this builder
         */
        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        /**
         * Sets when the job completed.
         *
         * @param completedAt the completion time (default: now)
         * @return this builder
         */
        public Builder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        /**
         * Sets the job output message.
         *
         * @param output the output message (max 10KB)
         * @return this builder
         */
        public Builder output(String output) {
            this.output = output;
            return this;
        }

        /**
         * Sets the error message (for FAILED status).
         *
         * @param error the error message (max 10KB)
         * @return this builder
         */
        public Builder error(String error) {
            this.error = error;
            return this;
        }

        /**
         * Adds a metadata entry.
         *
         * @param key the metadata key
         * @param value the metadata value
         * @return this builder
         */
        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        /**
         * Sets all metadata entries.
         *
         * @param metadata the metadata map
         * @return this builder
         */
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata.clear();
            this.metadata.putAll(metadata);
            return this;
        }

        public PingOptions build() {
            return new PingOptions(this);
        }
    }
}
