# BearWatch Java SDK

Official Java SDK for [BearWatch](https://bearwatch.dev) - Job monitoring and alerting for developers.

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.github.bearwatch-util:bearwatch-java-sdk:0.1.2")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'io.github.bearwatch-util:bearwatch-java-sdk:0.1.2'
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.bearwatch-util</groupId>
    <artifactId>bearwatch-java-sdk</artifactId>
    <version>0.1.2</version>
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

### Quick Reference

| Want to...                        | Use this                                              |
|-----------------------------------|-------------------------------------------------------|
| Report success quickly            | `bw.ping(jobId)` or `bw.ping(jobId, RequestStatus.SUCCESS)` |
| Report failure with error         | `bw.ping(jobId, RequestStatus.FAILED, "error message")` |
| Auto-track execution time         | `bw.wrap(jobId, () -> { ... })`                       |
| Report long-running job started   | `bw.ping(jobId, RequestStatus.RUNNING)`               |
| Non-blocking report               | `bw.pingAsync(jobId, callback)`                       |

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

#### wrap with options

Include output and metadata with automatic timing:

```java
bw.wrap("507f1f77bcf86cd799439011", WrapOptions.builder()
    .output("Backup completed successfully")
    .metadata("server", "backup-01")
    .metadata("region", "ap-northeast-2")
    .metadata("version", "1.2.0")
    .build(), () -> {
    performBackup();
});

// With return value
int count = bw.wrap("507f1f77bcf86cd799439011", WrapOptions.builder()
    .output("Processed records")
    .metadata("server", "backup-01")
    .build(), () -> {
    return processRecords();
});
```

#### WrapOptions

| Option     | Type                  | Default | Description                           |
|------------|-----------------------|---------|---------------------------------------|
| `output`   | `String`              | -       | Output message (max 10KB)             |
| `metadata` | `Map<String, Object>` | -       | Additional key-value pairs (max 10KB) |

> **Note**: `wrap` automatically sets `startedAt`, `completedAt`, and `status` (SUCCESS/FAILED). Use `ping` with `PingOptions` if you need manual control over these fields.

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
        .metadata("server", "backup-01")
        .metadata("region", "ap-northeast-2")
        .metadata("version", "1.2.0")
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

> **Size Limits**: The `output`, `error`, and `metadata` fields have a 10KB size limit. If exceeded, the server automatically truncates the data (no error is returned):
> - `output` and `error`: Strings are truncated to fit within 10KB
> - `metadata`: Set to `null` if the serialized size exceeds 10KB

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

// Async ping with options
bw.pingAsync("507f1f77bcf86cd799439011", PingOptions.builder()
    .status(RequestStatus.SUCCESS)
    .output("Processed 1000 records")
    .metadata("server", "web-01")
    .build(), new ResultCallback<HeartbeatResponse>() {
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

> **Note**: `wrap` uses `Callable<T>` (can throw checked exceptions), while `wrapAsync` uses `Supplier<CompletableFuture<T>>` (returns a future).

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

`BearWatchException` extends `RuntimeException`, so it's an unchecked exception. When the SDK fails to communicate with BearWatch (network failure, server down, invalid API key, etc.), it throws a `BearWatchException`:

> **Note**: Since `BearWatchException` is a `RuntimeException`, place its catch block before generic `Exception` catches to handle SDK errors specifically.

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

### Error Context

For debugging, exceptions include context information about the failed operation:

```java
try {
    bw.ping("507f1f77bcf86cd799439011");
} catch (BearWatchException e) {
    BearWatchException.ErrorContext ctx = e.getContext();
    if (ctx != null) {
        System.err.println("Job ID: " + ctx.getJobId());
        System.err.println("Operation: " + ctx.getOperation());  // "ping", "wrap", "pingAsync", etc.
        System.err.println("Run ID: " + ctx.getRunId());         // null if not yet created
    }
}
```

## Types

The SDK provides clear type separation for requests vs responses:

```java
import io.bearwatch.sdk.BearWatch;
import io.bearwatch.sdk.BearWatchConfig;
import io.bearwatch.sdk.BearWatchException;
import io.bearwatch.sdk.callback.ResultCallback;  // For async callbacks
import io.bearwatch.sdk.model.HeartbeatResponse;
import io.bearwatch.sdk.options.PingOptions;
import io.bearwatch.sdk.options.WrapOptions;
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
    void wrap(String jobId, WrapOptions options, Runnable task);
    <T> T wrap(String jobId, Callable<T> task);
    <T> T wrap(String jobId, WrapOptions options, Callable<T> task);

    // Async methods (callback-based)
    void pingAsync(String jobId, ResultCallback<HeartbeatResponse> callback);
    void pingAsync(String jobId, PingOptions options, ResultCallback<HeartbeatResponse> callback);

    // Async methods (CompletableFuture-based)
    <T> CompletableFuture<T> wrapAsync(String jobId, Supplier<CompletableFuture<T>> task);
    <T> CompletableFuture<T> wrapAsync(String jobId, WrapOptions options, Supplier<CompletableFuture<T>> task);

    void close();
}
```

### HeartbeatResponse

The response returned from `ping()` contains:

| Field        | Type      | Description                                |
|--------------|-----------|--------------------------------------------|
| `runId`      | `String`  | Unique ID for this run                     |
| `jobId`      | `String`  | The job ID                                 |
| `status`     | `Status`  | Recorded status (SUCCESS, FAILED, etc.)    |
| `receivedAt` | `Instant` | Timestamp when server received the request |

```java
HeartbeatResponse response = bw.ping("507f1f77bcf86cd799439011");
System.out.println("Run ID: " + response.getRunId());
System.out.println("Received at: " + response.getReceivedAt());
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

### Long-Running Jobs with RUNNING Status

Use `RUNNING` status to track jobs that take significant time (minutes to hours). This enables:
- **Real-time visibility**: Dashboard shows the job is actively executing
- **Accurate duration**: Capture precise `startedAt` before work begins
- **Stuck job detection**: Server can detect if a job stays in RUNNING too long

#### Basic Pattern: RUNNING → SUCCESS/FAILED

```java
public void runLongBackup() {
    String jobId = "6848c9e5f8a2b3d4e5f60003";
    Instant startedAt = Instant.now();

    // 1. Report RUNNING immediately when job starts
    bw.ping(jobId, RequestStatus.RUNNING);

    try {
        performBackup();

        // 2. Report SUCCESS with accurate timing
        bw.ping(jobId, PingOptions.builder()
            .status(RequestStatus.SUCCESS)
            .startedAt(startedAt)
            .completedAt(Instant.now())
            .build());
    } catch (Exception e) {
        // 3. Report FAILED with error details
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

#### Progress Updates for Very Long Jobs

For jobs running 30+ minutes, send periodic RUNNING pings to show progress:

```java
public void runDataMigration() {
    String jobId = "6848c9e5f8a2b3d4e5f60003";
    Instant startedAt = Instant.now();

    bw.ping(jobId, PingOptions.builder()
        .status(RequestStatus.RUNNING)
        .output("Starting migration...")
        .build());

    try {
        List<Table> tables = getTablesToMigrate();
        for (int i = 0; i < tables.size(); i++) {
            migrateTable(tables.get(i));

            // Progress update every N tables
            if ((i + 1) % 10 == 0) {
                bw.ping(jobId, PingOptions.builder()
                    .status(RequestStatus.RUNNING)
                    .output(String.format("Progress: %d/%d tables", i + 1, tables.size()))
                    .build());
            }
        }

        bw.ping(jobId, PingOptions.builder()
            .status(RequestStatus.SUCCESS)
            .startedAt(startedAt)
            .completedAt(Instant.now())
            .output("Migrated " + tables.size() + " tables")
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

> **When to use RUNNING**: Jobs over 1 minute. For quick jobs (< 1 minute), use `wrap()` instead—it's simpler and handles timing automatically.

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
