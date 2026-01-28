package io.bearwatch.sdk.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import io.bearwatch.sdk.BearWatchConfig;
import io.bearwatch.sdk.BearWatchException;
import io.bearwatch.sdk.callback.ResultCallback;
import io.bearwatch.sdk.model.ApiResponse;
import okhttp3.*;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Internal HTTP client using OkHttp.
 */
public class HttpClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String USER_AGENT = "bearwatch-sdk-java/0.1.0";

    private final OkHttpClient client;
    private final BearWatchConfig config;
    private final JsonMapper jsonMapper;
    private final RetryPolicy retryPolicy;

    public HttpClient(BearWatchConfig config) {
        this.config = config;
        this.jsonMapper = new JsonMapper();
        this.retryPolicy = new RetryPolicy(config.getMaxRetries(), config.getRetryDelay());

        this.client = new OkHttpClient.Builder()
                .connectTimeout(config.getTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(config.getTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .writeTimeout(config.getTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .build();
    }

    /**
     * Sends a synchronous POST request with retry.
     *
     * @param path the API path (e.g., "/api/v1/ingest/jobs/{jobId}/heartbeat")
     * @param body the request body object
     * @param responseType the type reference for the response
     * @param <T> the response data type
     * @return the response data
     * @throws BearWatchException if the request fails
     */
    public <T> T post(String path, Object body, TypeReference<ApiResponse<T>> responseType) {
        return post(path, body, responseType, true);
    }

    /**
     * Sends a synchronous POST request with optional retry.
     *
     * @param path the API path (e.g., "/api/v1/ingest/jobs/{jobId}/heartbeat")
     * @param body the request body object
     * @param responseType the type reference for the response
     * @param retry whether to apply retry policy
     * @param <T> the response data type
     * @return the response data
     * @throws BearWatchException if the request fails
     */
    public <T> T post(String path, Object body, TypeReference<ApiResponse<T>> responseType, boolean retry) {
        if (retry) {
            return retryPolicy.execute(() -> doPost(path, body, responseType));
        } else {
            return doPost(path, body, responseType);
        }
    }

    /**
     * Sends an asynchronous POST request.
     *
     * @param path the API path
     * @param body the request body object
     * @param responseType the type reference for the response
     * @param callback the callback to invoke on completion
     * @param <T> the response data type
     */
    public <T> void postAsync(String path, Object body, TypeReference<ApiResponse<T>> responseType, ResultCallback<T> callback) {
        Request request = buildRequest(path, body);

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(BearWatchException.networkError(e));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (response) {
                    T result = handleResponse(response, responseType, path);
                    callback.onSuccess(result);
                } catch (BearWatchException e) {
                    callback.onFailure(e);
                } catch (Exception e) {
                    callback.onFailure(new BearWatchException("Unexpected error", e));
                }
            }
        });
    }

    /**
     * Sends an asynchronous POST request returning a CompletableFuture.
     *
     * @param path the API path
     * @param body the request body object
     * @param responseType the type reference for the response
     * @param <T> the response data type
     * @return a CompletableFuture that completes with the response
     */
    public <T> CompletableFuture<T> postFuture(String path, Object body, TypeReference<ApiResponse<T>> responseType) {
        return postFuture(path, body, responseType, true);
    }

    /**
     * Sends an asynchronous POST request returning a CompletableFuture with optional retry.
     *
     * @param path the API path
     * @param body the request body object
     * @param responseType the type reference for the response
     * @param retry whether to apply retry policy
     * @param <T> the response data type
     * @return a CompletableFuture that completes with the response
     */
    public <T> CompletableFuture<T> postFuture(String path, Object body, TypeReference<ApiResponse<T>> responseType, boolean retry) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Request request = buildRequest(path, body);

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                future.completeExceptionally(BearWatchException.networkError(e));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (response) {
                    T result = handleResponse(response, responseType, path);
                    future.complete(result);
                } catch (BearWatchException e) {
                    future.completeExceptionally(e);
                } catch (Exception e) {
                    future.completeExceptionally(new BearWatchException("Unexpected error", e));
                }
            }
        });

        return future;
    }

    private <T> T doPost(String path, Object body, TypeReference<ApiResponse<T>> responseType) {
        Request request = buildRequest(path, body);

        try (Response response = client.newCall(request).execute()) {
            return handleResponse(response, responseType, path);
        } catch (IOException e) {
            throw BearWatchException.networkError(e);
        }
    }

    private Request buildRequest(String path, Object body) {
        String url = config.getBaseUrl() + path;
        String json = jsonMapper.toJson(body);

        return new Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header(API_KEY_HEADER, config.getApiKey())
                .header("User-Agent", USER_AGENT)
                .post(RequestBody.create(json, JSON))
                .build();
    }

    private <T> T handleResponse(Response response, TypeReference<ApiResponse<T>> responseType, String path) {
        int statusCode = response.code();
        String responseBody;

        try {
            responseBody = response.body() != null ? response.body().string() : "";
        } catch (IOException e) {
            throw BearWatchException.networkError(e);
        }

        // Handle HTTP-level errors
        if (statusCode == 401) {
            throw BearWatchException.invalidApiKey();
        }

        if (statusCode == 404) {
            throw BearWatchException.jobNotFound(extractJobId(path));
        }

        if (statusCode == 429) {
            String retryAfterHeader = response.header("Retry-After");
            Long retryAfterMs = parseRetryAfter(retryAfterHeader);
            throw BearWatchException.rateLimited(retryAfterMs, responseBody);
        }

        if (statusCode >= 500) {
            throw BearWatchException.serverError(statusCode, responseBody);
        }

        // Parse response - 비JSON 응답 처리 개선
        ApiResponse<T> apiResponse;
        try {
            apiResponse = jsonMapper.fromJson(responseBody, responseType);
        } catch (BearWatchException e) {
            throw BearWatchException.invalidResponse(
                    "Non-JSON or malformed response: " + e.getMessage(), statusCode, responseBody, e);
        }

        if (!apiResponse.isSuccess()) {
            ApiResponse.ErrorInfo error = apiResponse.getError();
            String errorCode = error != null ? error.getCode() : "UNKNOWN_ERROR";
            String errorMessage = error != null ? error.getMessage() : "Unknown error";
            throw new BearWatchException(statusCode, errorCode, errorMessage, responseBody);
        }

        return apiResponse.getData();
    }

    /**
     * Extracts jobId from API path.
     * Path format: /api/v1/ingest/jobs/{jobId}/heartbeat
     */
    private String extractJobId(String path) {
        if (path == null) {
            return "unknown";
        }
        String[] parts = path.split("/");
        // /api/v1/ingest/jobs/{jobId}/heartbeat → parts[5] = jobId
        return parts.length >= 6 ? parts[5] : "unknown";
    }

    /**
     * Parses the Retry-After header value.
     * Supports both seconds (e.g., "120") and HTTP-date (e.g., "Wed, 22 Jan 2026 07:28:00 GMT").
     *
     * @param value the Retry-After header value
     * @return the delay in milliseconds, or null if not parseable
     */
    private Long parseRetryAfter(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        // Try parsing as seconds
        try {
            long seconds = Long.parseLong(value.trim());
            return seconds * 1000;
        } catch (NumberFormatException ignored) {
            // Not a number, try HTTP-date
        }

        // Try parsing as HTTP-date (RFC 1123)
        try {
            ZonedDateTime date = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME);
            long delayMs = date.toInstant().toEpochMilli() - System.currentTimeMillis();
            return Math.max(0, delayMs);
        } catch (DateTimeParseException ignored) {
            // Not a valid HTTP-date
        }

        return null;
    }

    /**
     * Closes the HTTP client and releases resources.
     */
    public void close() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}
