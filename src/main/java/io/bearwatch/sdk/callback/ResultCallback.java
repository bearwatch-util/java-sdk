package io.bearwatch.sdk.callback;

import io.bearwatch.sdk.BearWatchException;

/**
 * Callback interface for async operations.
 *
 * @param <T> the result type
 */
public interface ResultCallback<T> {

    /**
     * Called when the operation completes successfully.
     *
     * @param result the result value
     */
    void onSuccess(T result);

    /**
     * Called when the operation fails.
     *
     * @param exception the exception that caused the failure
     */
    void onFailure(BearWatchException exception);
}
