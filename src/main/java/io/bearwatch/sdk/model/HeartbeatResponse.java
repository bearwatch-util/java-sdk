package io.bearwatch.sdk.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Response from the BearWatch API after sending a heartbeat.
 */
public class HeartbeatResponse {

    private final String runId;
    private final String jobId;
    private final Status status;
    private final Instant receivedAt;

    @JsonCreator
    public HeartbeatResponse(
            @JsonProperty("runId") String runId,
            @JsonProperty("jobId") String jobId,
            @JsonProperty("status") Status status,
            @JsonProperty("receivedAt") Instant receivedAt
    ) {
        this.runId = runId;
        this.jobId = jobId;
        this.status = status;
        this.receivedAt = receivedAt;
    }

    /**
     * Returns the unique ID for this run.
     */
    public String getRunId() {
        return runId;
    }

    /**
     * Returns the job ID.
     */
    public String getJobId() {
        return jobId;
    }

    /**
     * Returns the recorded status.
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Returns the timestamp when the server received the heartbeat.
     */
    public Instant getReceivedAt() {
        return receivedAt;
    }

    @Override
    public String toString() {
        return "HeartbeatResponse{" +
                "runId='" + runId + '\'' +
                ", jobId='" + jobId + '\'' +
                ", status=" + status +
                ", receivedAt=" + receivedAt +
                '}';
    }
}
