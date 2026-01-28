package io.bearwatch.sdk.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Wrapper for BearWatch API responses.
 *
 * @param <T> the type of data in a successful response
 */
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ErrorInfo error;

    @JsonCreator
    public ApiResponse(
            @JsonProperty("success") boolean success,
            @JsonProperty("data") T data,
            @JsonProperty("error") ErrorInfo error
    ) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    public boolean isSuccess() {
        return success;
    }

    public T getData() {
        return data;
    }

    public ErrorInfo getError() {
        return error;
    }

    /**
     * Error information from failed API responses.
     */
    public static class ErrorInfo {
        private final String code;
        private final String message;

        @JsonCreator
        public ErrorInfo(
                @JsonProperty("code") String code,
                @JsonProperty("message") String message
        ) {
            this.code = code;
            this.message = message;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return "ErrorInfo{code='" + code + "', message='" + message + "'}";
        }
    }
}
