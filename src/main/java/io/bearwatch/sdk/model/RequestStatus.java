package io.bearwatch.sdk.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Status values that can be sent in requests.
 * Only these 3 values are valid for ping operations.
 * <p>
 * For response status values (including TIMEOUT and MISSED), use {@link Status}.
 *
 * @see Status for response status values
 */
public enum RequestStatus {
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
    FAILED("FAILED");

    private final String value;

    RequestStatus(String value) {
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

    /**
     * Converts this RequestStatus to the corresponding Status enum value.
     *
     * @return the corresponding Status value
     */
    public Status toStatus() {
        return Status.valueOf(this.name());
    }
}
