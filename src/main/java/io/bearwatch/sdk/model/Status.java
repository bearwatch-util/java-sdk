package io.bearwatch.sdk.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Represents the status of a job run.
 */
public enum Status {
    /**
     * Job is currently running
     */
    RUNNING("RUNNING"),

    /**
     * Job completed successfully
     */
    SUCCESS("SUCCESS"),

    /**
     * Job failed with an error
     */
    FAILED("FAILED"),

    /**
     * Job timed out (server-side detection)
     */
    TIMEOUT("TIMEOUT"),

    /**
     * Job run was missed (server-side detection)
     */
    MISSED("MISSED");

    private final String value;

    Status(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
