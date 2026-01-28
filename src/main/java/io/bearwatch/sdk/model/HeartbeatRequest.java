package io.bearwatch.sdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Request body for sending a heartbeat to the BearWatch API.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HeartbeatRequest {

    private final RequestStatus status;
    private final Instant startedAt;
    private final Instant completedAt;
    private final String output;
    private final String error;
    private final Map<String, Object> metadata;

    public HeartbeatRequest(
            RequestStatus status,
            Instant startedAt,
            Instant completedAt,
            String output,
            String error,
            Map<String, Object> metadata
    ) {
        this.status = status;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.output = output;
        this.error = error;
        this.metadata = metadata;
    }

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
     * Creates a simple success heartbeat request.
     * Uses current time for both startedAt and completedAt.
     */
    public static HeartbeatRequest success() {
        Instant now = Instant.now();
        return new HeartbeatRequest(RequestStatus.SUCCESS, now, now, null, null, null);
    }

    /**
     * Creates a simple failed heartbeat request.
     * Uses current time for both startedAt and completedAt.
     */
    public static HeartbeatRequest failed(String error) {
        Instant now = Instant.now();
        return new HeartbeatRequest(RequestStatus.FAILED, now, now, null, error, null);
    }

    /**
     * Creates a running status heartbeat request.
     * Uses current time for both startedAt and completedAt.
     */
    public static HeartbeatRequest running() {
        Instant now = Instant.now();
        return new HeartbeatRequest(RequestStatus.RUNNING, now, now, null, null, null);
    }
}
