# BearWatch Java SDK

Official Java SDK for [BearWatch](https://bearwatch.dev) - Cron/Job Monitoring for developers.

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.bearwatch:bearwatch-sdk:0.1.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'io.bearwatch:bearwatch-sdk:0.1.0'
}
```

### Maven

```xml
<dependency>
    <groupId>io.bearwatch</groupId>
    <artifactId>bearwatch-sdk</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Quick Start

```java
import io.bearwatch.sdk.BearWatch;
import io.bearwatch.sdk.BearWatchConfig;

// 1. Create client
BearWatch bw = BearWatch.create(BearWatchConfig.builder("bw_your_api_key").build());

// 2. Monitor your job
bw.wrap("my-backup-job", () -> {
    // Your job logic here
    performBackup();
});
```

## Usage

### Simple Ping

The simplest way to monitor a job - call `ping()` when it completes:

```java
import io.bearwatch.sdk.model.RequestStatus;

// Success ping
bw.ping("my-job");

// Failed ping with error message
bw.ping("my-job", RequestStatus.FAILED, "Database connection failed");
```

### Ping with Options

Include additional details with your heartbeat:

```java
bw.ping("etl-job", PingOptions.builder()
    .status(RequestStatus.SUCCESS)
    .output("Processed 10,000 records")
    .metadata("recordCount", 10000)
    .metadata("source", "postgres")
    .build());

// With manual timing
Instant startedAt = Instant.now();
doWork();
bw.ping("etl-job", PingOptions.builder()
    .status(RequestStatus.SUCCESS)
    .startedAt(startedAt)
    .completedAt(Instant.now())
    .build());
```

### Wrap Pattern

The easiest way to monitor a job - wrap it and let the SDK handle everything:

```java
// Void task
bw.wrap("cleanup-job", () -> {
    cleanupOldFiles();
    // Automatically sends SUCCESS on completion
    // Automatically sends FAILED with error message on exception
});

// Task with return value
int count = bw.wrap("count-job", () -> {
    return countRecords();
});
```

### Async Operations

For non-blocking monitoring, use the async variants:

#### wrapAsync - Async task wrapping with CompletableFuture

```java
CompletableFuture<ProcessResult> future = bw.wrapAsync("async-job", () -> {
    return someAsyncOperation();  // Returns CompletableFuture<ProcessResult>
});

// The SDK automatically sends SUCCESS or FAILED when the future completes
future.thenAccept(result -> System.out.println("Done: " + result));
```

#### completeAsync / failAsync - Fire-and-forget completion

```java
// Send completion without blocking
bw.completeAsync("my-job")
    .thenAccept(response -> log.info("Recorded: {}", response.getRunId()))
    .exceptionally(e -> { log.error("Failed to record", e); return null; });

// Send failure without blocking
bw.failAsync("my-job", "Connection timeout")
    .exceptionally(e -> { log.error("Failed to record", e); return null; });
```

#### pingAsync - Callback-based async ping

```java
bw.pingAsync("my-job", new ResultCallback<HeartbeatResponse>() {
    @Override
    public void onSuccess(HeartbeatResponse response) {
        System.out.println("Run recorded: " + response.getRunId());
    }

    @Override
    public void onFailure(BearWatchException e) {
        logger.error("BearWatch error: {}", e.getMessage());
    }
});
```

> **Warning**: Async methods do NOT retry automatically. They make a single attempt only.
> For reliable delivery with automatic retries, use the synchronous APIs (`ping()`, `wrap()`, etc.).

## Retry Policy

The SDK includes automatic retry with exponential backoff for transient errors:

| Method | Retry | Reason |
|--------|-------|--------|
| `ping()` | ✅ Yes | Idempotent operation |
| `wrap()` | ✅ Yes | Uses ping() internally |
| `complete()` | ✅ Yes | Idempotent operation |
| `fail()` | ✅ Yes | Idempotent operation |
| `pingAsync()` | ❌ No | Async operations are single-attempt |
| `wrapAsync()` | ❌ No | Async operations are single-attempt |
| `completeAsync()` | ❌ No | Async operations are single-attempt |
| `failAsync()` | ❌ No | Async operations are single-attempt |

### Retryable Errors

- **5xx Server Errors**: Automatically retried with exponential backoff
- **429 Rate Limited**: Retried after `Retry-After` header delay (if present) or default backoff
- **Network Errors**: Automatically retried

### 429 Rate Limiting

When the server returns 429 (Too Many Requests), the SDK respects the `Retry-After` header:

```java
// If server returns: 429 with Retry-After: 60
// SDK will wait 60 seconds before retrying
```

### Configuring Retry

```java
BearWatchConfig config = BearWatchConfig.builder("bw_your_api_key")
    .maxRetries(3)                          // Number of retry attempts (default: 3)
    .retryDelay(Duration.ofMillis(500))     // Base delay for exponential backoff (default: 500ms)
    .build();
```

## Configuration

```java
BearWatchConfig config = BearWatchConfig.builder("bw_your_api_key")
    .baseUrl("https://api.bearwatch.dev")  // Default
    .timeout(Duration.ofSeconds(30))        // Default: 30s
    .maxRetries(3)                          // Default: 3 (sync only)
    .retryDelay(Duration.ofMillis(500))     // Default: 500ms
    .onError(e -> log.error("Error", e))    // Optional global error handler
    .build();
```

## Spring Boot Integration

```java
import io.bearwatch.sdk.BearWatch;
import io.bearwatch.sdk.BearWatchConfig;

@Configuration
public class BearWatchConfiguration {

    @Bean
    public BearWatch bearWatch(@Value("${bearwatch.api-key}") String apiKey) {
        return BearWatch.create(BearWatchConfig.builder(apiKey).build());
    }
}

@Component
@RequiredArgsConstructor
public class DailyReportJob {
    private final BearWatch bearWatch;

    @Scheduled(cron = "0 0 9 * * *")
    public void generateDailyReport() {
        bearWatch.wrap("daily-report", () -> {
            // Generate and send report
        });
    }
}
```

## Error Handling

```java
try {
    bw.ping("my-job");
} catch (BearWatchException e) {
    System.err.println("Status code: " + e.getStatusCode());
    System.err.println("Error code: " + e.getErrorCode());
    System.err.println("Message: " + e.getMessage());

    // Error context provides additional debugging information
    if (e.getContext() != null) {
        System.err.println("Job ID: " + e.getContext().getJobId());
        System.err.println("Operation: " + e.getContext().getOperation());
    }

    // For 429 errors, check retry delay
    if (e.getRetryAfterMs() != null) {
        System.err.println("Retry after: " + e.getRetryAfterMs() + "ms");
    }
}
```

### Error Context

All exceptions include context information for easier debugging:

```java
try {
    bw.ping("my-job");
} catch (BearWatchException e) {
    BearWatchException.ErrorContext ctx = e.getContext();
    // ctx.getJobId()      -> "my-job"
    // ctx.getOperation()  -> "ping", "complete", "fail", "pingAsync", "completeAsync", "failAsync"
    // ctx.getRunId()      -> null (or runId if applicable)
}
```

## API Reference

### BearWatch

| Method | Description |
|--------|-------------|
| `ping(jobId)` | Send success heartbeat |
| `ping(jobId, status)` | Send heartbeat with status |
| `ping(jobId, status, error)` | Send heartbeat with status and error message |
| `ping(jobId, options)` | Send heartbeat with options |
| `pingAsync(jobId, callback)` | Send heartbeat asynchronously (callback) |
| `wrap(jobId, runnable)` | Wrap a void task |
| `wrap(jobId, callable)` | Wrap a task with return value |
| `wrapAsync(jobId, supplier)` | Wrap an async task returning CompletableFuture |
| `complete(jobId)` | Send success completion |
| `complete(jobId, options)` | Send success completion with options |
| `completeAsync(jobId)` | Send success completion asynchronously |
| `completeAsync(jobId, options)` | Send success completion with options asynchronously |
| `fail(jobId, error)` | Send failure with error message |
| `fail(jobId, throwable)` | Send failure with exception |
| `failAsync(jobId, error)` | Send failure asynchronously |
| `failAsync(jobId, throwable)` | Send failure with exception asynchronously |
| `close()` | Release resources |

### RequestStatus (for requests)

Use `RequestStatus` when sending heartbeats:

| Value | Description |
|-------|-------------|
| `SUCCESS` | Job completed successfully |
| `FAILED` | Job failed with error |
| `RUNNING` | Job is currently running |

### Status (for responses)

`Status` includes all values that can appear in server responses:

| Value | Description |
|-------|-------------|
| `SUCCESS` | Job completed successfully |
| `FAILED` | Job failed with error |
| `RUNNING` | Job is currently running |
| `TIMEOUT` | Job timed out (server-detected) |
| `MISSED` | Job run was missed (server-detected) |

> **Note**: `TIMEOUT` and `MISSED` are server-detected states. Do not use them in requests - use `RequestStatus` instead.

## Requirements

- Java 11 or higher

## License

MIT License
