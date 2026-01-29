# BearWatch Java SDK

Official Java SDK for [BearWatch](https://bearwatch.dev) - Job monitoring and alerting for developers.

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.github.bearwatch-util:bearwatch-java-sdk:0.1.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'io.github.bearwatch-util:bearwatch-java-sdk:0.1.0'
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.bearwatch-util</groupId>
    <artifactId>bearwatch-java-sdk</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Requirements

- **Java 11 or higher**

## Quick Start

### 1. Get API Key

Go to [BearWatch Dashboard](https://bearwatch.dev) → Project Settings → Create API Key (e.g., `bw_kI6t8QA21on0DKeRDlen8r2hzucVNL3WdAfaZgQdetY`).

### 2. Create a Job

Create a job in the dashboard. You'll get a job ID (24-character hex string, e.g., `507f1f77bcf86cd799439011`).

### 3. Install and Use

Let's assume you have a daily backup job that runs at 2:00 AM:

```java
import io.bearwatch.sdk.BearWatch;
import io.bearwatch.sdk.BearWatchConfig;

public class BackupJob {
    private final BearWatch bw = BearWatch.create(
        BearWatchConfig.builder("your-api-key").build()
    );

    @Scheduled(cron = "0 0 2 * * *")
    public void runBackup() {
        bw.wrap("507f1f77bcf86cd799439011", () -> {
            performBackup();
        });
    }
}
```

## Usage

### wrap - Automatic Status Reporting

Wraps a function and automatically:
- Measures `startedAt` and `completedAt`
- Reports `SUCCESS` or `FAILED` based on whether the function completes or throws

```java
// Void task
bw.wrap("507f1f77bcf86cd799439011", () -> {
    performBackup();
});

// Task with return value
int count = bw.wrap("507f1f77bcf86cd799439011", () -> {
    return countRecords();
});
```

**Error handling behavior:**
- On success: reports `SUCCESS` with execution duration
- On error: reports `FAILED` with error message, then **re-throws the original exception**

```java
@Scheduled(cron = "0 0 2 * * *")
public void runBackup() {
    try {
        bw.wrap("507f1f77bcf86cd799439011", () -> {
            performBackup();
        });
    } catch (Exception e) {
        // BearWatch already reported FAILED status
        // You can add additional error handling here
        logger.error("Backup failed", e);
    }
}
```

> **Tip**: Use `wrap` for most cases. Use `ping` when you need more control (e.g., reporting RUNNING status for long jobs).

### ping - Manual Status Reporting

Use `ping` when you need fine-grained control over status reporting:

```java
import io.bearwatch.sdk.model.RequestStatus;

@Scheduled(cron = "0 0 2 * * *")
public void runBackup() {
    try {
        performBackup();
        bw.ping("507f1f77bcf86cd799439011", RequestStatus.SUCCESS);
    } catch (Exception e) {
        bw.ping("507f1f77bcf86cd799439011", RequestStatus.FAILED, e.getMessage());
    }
}
```

Include output and metadata:

```java
@Scheduled(cron = "0 0 0 * * *")
public void runBackup() {
    long bytes = performBackup();
    bw.ping("507f1f77bcf86cd799439011", PingOptions.builder()
        .status(RequestStatus.SUCCESS)
        .output("Backup completed: " + bytes + " bytes")
        .metadata("bytes", bytes)
        .build());
}
```

#### PingOptions

| Option        | Type                   | Default        | Description                                     |
|---------------|------------------------|----------------|-------------------------------------------------|
| `status`      | `RequestStatus`        | `SUCCESS`      | `RUNNING`, `SUCCESS`, or `FAILED`               |
| `output`      | `String`               | -              | Output message (max 10KB)                       |
| `error`       | `String`               | -              | Error message for `FAILED` status (max 10KB)    |
| `startedAt`   | `Instant`              | current time   | Job start time                                  |
| `completedAt` | `Instant`              | current time   | Job completion time                             |
| `metadata`    | `Map<String, Object>`  | -              | Additional key-value pairs (max 10KB)           |

> **Note**: `TIMEOUT` and `MISSED` are server-detected states and cannot be set in requests.

### Async Operations

For non-blocking monitoring, use the async variants:

```java
// Async ping with callback
bw.pingAsync("507f1f77bcf86cd799439011", new ResultCallback<HeartbeatResponse>() {
    @Override
    public void onSuccess(HeartbeatResponse result) {
        logger.info("Recorded: {}", result.getRunId());
    }

    @Override
    public void onFailure(BearWatchException error) {
        logger.error("Failed to record", error);
    }
});

// Async task wrapping
CompletableFuture<ProcessResult> future = bw.wrapAsync("507f1f77bcf86cd799439011", () -> {
    return someAsyncOperation();  // Returns CompletableFuture<ProcessResult>
});
```

> **Warning**: Async methods do NOT retry automatically. They make a single attempt only. For reliable delivery with automatic retries, use the synchronous APIs (`ping()`, `wrap()`, etc.).

## Configuration

```java
BearWatch bw = BearWatch.create(BearWatchConfig.builder("your-api-key")
    // Optional (defaults shown)
    .timeout(Duration.ofSeconds(30))        // 30 seconds
    .maxRetries(3)
    .retryDelay(Duration.ofMillis(500))     // 500ms base delay
    .onError(e -> logger.error("BearWatch error", e))  // Global error handler
    .build());
```

| Option       | Type                              | Required | Default    | Description                |
|--------------|-----------------------------------|----------|------------|----------------------------|
| `apiKey`     | `String`                          | Yes      | -          | API key for authentication |
| `timeout`    | `Duration`                        | No       | 30 seconds | Request timeout            |
| `maxRetries` | `int`                             | No       | `3`        | Max retry attempts         |
| `retryDelay` | `Duration`                        | No       | 500ms      | Initial retry delay        |
| `onError`    | `Consumer<BearWatchException>`    | No       | -          | Global error handler       |

## Retry Policy

| Method           | Default Retry | Reason                         |
|------------------|---------------|--------------------------------|
| `ping()`         | Enabled       | Idempotent operation           |
| `wrap()`         | Enabled       | Uses ping() internally         |
| `pingAsync()`    | Disabled      | Async operations single-attempt|
| `wrapAsync()`    | Disabled      | Async operations single-attempt|

### Retry Behavior

- **Exponential backoff with jitter**: ~500-1000ms → ~1000-2000ms → ~2000-4000ms
- **429 Rate Limit**: Respects `Retry-After` header (rate limit: 100 requests/minute per API key)
- **5xx Server Errors**: Retries with backoff
- **401/404**: No retry (client errors)

## Error Handling

When the SDK fails to communicate with BearWatch (network failure, server down, invalid API key, etc.), it throws a `BearWatchException`:

```java
import io.bearwatch.sdk.BearWatch;
import io.bearwatch.sdk.BearWatchException;

try {
    bw.ping("507f1f77bcf86cd799439011");
} catch (BearWatchException e) {
    // SDK failed to report to BearWatch
    System.err.println("Code: " + e.getErrorCode());
    System.err.println("Status: " + e.getStatusCode());
}
```

### Error Codes

| Code              | Description              | Retry   |
|-------------------|--------------------------|---------|
| `INVALID_API_KEY` | 401 - Invalid API key    | No      |
| `JOB_NOT_FOUND`   | 404 - Job not found      | No      |
| `RATE_LIMITED`    | 429 - Rate limit reached | Yes     |
| `SERVER_ERROR`    | 5xx - Server error       | Yes     |
| `NETWORK_ERROR`   | Network failure          | Yes     |
| `TIMEOUT`         | Request timed out        | Yes     |

## Types

The SDK provides clear type separation for requests vs responses:

```java
import io.bearwatch.sdk.BearWatch;
import io.bearwatch.sdk.BearWatchConfig;
import io.bearwatch.sdk.BearWatchException;
import io.bearwatch.sdk.model.HeartbeatResponse;
import io.bearwatch.sdk.model.PingOptions;
import io.bearwatch.sdk.model.RequestStatus;   // For requests: RUNNING, SUCCESS, FAILED
import io.bearwatch.sdk.model.Status;          // For responses: includes TIMEOUT, MISSED
```

### Method Signatures

```java
public class BearWatch {
    // Sync methods
    HeartbeatResponse ping(String jobId);
    HeartbeatResponse ping(String jobId, RequestStatus status);
    HeartbeatResponse ping(String jobId, RequestStatus status, String error);
    HeartbeatResponse ping(String jobId, PingOptions options);

    void wrap(String jobId, Runnable task);
    <T> T wrap(String jobId, Callable<T> task);

    // Async methods (callback-based)
    void pingAsync(String jobId, ResultCallback<HeartbeatResponse> callback);
    void pingAsync(String jobId, PingOptions options, ResultCallback<HeartbeatResponse> callback);

    // Async methods (CompletableFuture-based)
    <T> CompletableFuture<T> wrapAsync(String jobId, Supplier<CompletableFuture<T>> task);

    void close();
}
```

## Common Patterns

### Spring Boot with @Scheduled

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
        bearWatch.wrap("6848c9e5f8a2b3d4e5f60001", () -> {
            generateAndSendReport();
        });
    }
}
```

### Quartz Scheduler

```java
public class BackupJob implements Job {
    private final BearWatch bw = BearWatch.create(
        BearWatchConfig.builder(System.getenv("BEARWATCH_API_KEY")).build()
    );

    @Override
    public void execute(JobExecutionContext context) {
        bw.wrap("6848c9e5f8a2b3d4e5f60002", () -> {
            performBackup();
        });
    }
}
```

### Long-Running Jobs

```java
public void runLongBackup() {
    String jobId = "6848c9e5f8a2b3d4e5f60003";
    Instant startedAt = Instant.now();

    bw.ping(jobId, RequestStatus.RUNNING);

    try {
        performBackup();
        bw.ping(jobId, PingOptions.builder()
            .status(RequestStatus.SUCCESS)
            .startedAt(startedAt)
            .completedAt(Instant.now())
            .build());
    } catch (Exception e) {
        bw.ping(jobId, PingOptions.builder()
            .status(RequestStatus.FAILED)
            .startedAt(startedAt)
            .completedAt(Instant.now())
            .error(e.getMessage())
            .build());
        throw e;
    }
}
```

### AWS Lambda

```java
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.bearwatch.sdk.BearWatch;
import io.bearwatch.sdk.BearWatchConfig;

public class BackupHandler implements RequestHandler<Object, String> {
    private final BearWatch bw = BearWatch.create(
        BearWatchConfig.builder(System.getenv("BEARWATCH_API_KEY")).build()
    );

    @Override
    public String handleRequest(Object input, Context context) {
        bw.wrap("6848c9e5f8a2b3d4e5f60004", () -> {
            performBackup();
        });
        return "OK";
    }
}
```

## FAQ

**Q: Do I need to create jobs in the dashboard first?**
A: Yes, create a job in the [BearWatch Dashboard](https://bearwatch.dev) first to get a job ID.

**Q: What's the difference between `wrap` and `ping`?**
A: `wrap` automatically measures execution time and reports SUCCESS/FAILED based on whether the function completes or throws. `ping` gives you manual control over when and what to report.

**Q: What happens if the SDK fails to report (network error)?**
A: By default, the SDK retries 3 times with exponential backoff. If all retries fail, `ping` throws a `BearWatchException`. For `wrap`, the original exception takes priority and is always re-thrown.

**Q: Should I call `close()` on the BearWatch instance?**
A: Yes, when your application shuts down, call `close()` to release HTTP client resources. In Spring Boot, define it as a `@Bean` and Spring will handle lifecycle management.

## License

MIT License
