package io.bearwatch.sdk.options;

import io.bearwatch.sdk.model.HeartbeatRequest;
import io.bearwatch.sdk.model.RequestStatus;
import io.bearwatch.sdk.model.Status;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PingOptionsTest {

    @Test
    void shouldBuildWithDefaults() {
        PingOptions options = PingOptions.builder().build();

        assertThat(options.getStatus()).isEqualTo(RequestStatus.SUCCESS);
        assertThat(options.getStartedAt()).isNull();
        assertThat(options.getCompletedAt()).isNull();
        assertThat(options.getOutput()).isNull();
        assertThat(options.getError()).isNull();
        assertThat(options.getMetadata()).isNull();
    }

    @Test
    void shouldBuildWithAllValues() {
        Instant startedAt = Instant.now().minusSeconds(10);
        Instant completedAt = Instant.now();

        PingOptions options = PingOptions.builder()
                .status(RequestStatus.FAILED)
                .startedAt(startedAt)
                .completedAt(completedAt)
                .output("Output message")
                .error("Error message")
                .metadata("key1", "value1")
                .metadata("key2", 42)
                .build();

        assertThat(options.getStatus()).isEqualTo(RequestStatus.FAILED);
        assertThat(options.getStartedAt()).isEqualTo(startedAt);
        assertThat(options.getCompletedAt()).isEqualTo(completedAt);
        assertThat(options.getOutput()).isEqualTo("Output message");
        assertThat(options.getError()).isEqualTo("Error message");
        assertThat(options.getMetadata()).containsEntry("key1", "value1");
        assertThat(options.getMetadata()).containsEntry("key2", 42);
    }

    @Test
    void shouldSetMetadataFromMap() {
        Map<String, Object> metadata = Map.of("key1", "value1", "key2", 123);

        PingOptions options = PingOptions.builder()
                .metadata(metadata)
                .build();

        assertThat(options.getMetadata()).hasSize(2);
        assertThat(options.getMetadata()).containsEntry("key1", "value1");
        assertThat(options.getMetadata()).containsEntry("key2", 123);
    }

    @Test
    void shouldConvertToRequest() {
        Instant startedAt = Instant.now().minusSeconds(5);

        PingOptions options = PingOptions.builder()
                .status(RequestStatus.SUCCESS)
                .startedAt(startedAt)
                .output("Success!")
                .metadata("count", 10)
                .build();

        HeartbeatRequest request = options.toRequest();

        assertThat(request.getStatus()).isEqualTo(RequestStatus.SUCCESS);
        assertThat(request.getStartedAt()).isEqualTo(startedAt);
        assertThat(request.getOutput()).isEqualTo("Success!");
        assertThat(request.getMetadata()).containsEntry("count", 10);
        assertThat(request.getCompletedAt()).isNotNull(); // Auto-set to now
    }

    @Test
    void shouldAutoSetStartedAtAndCompletedAtWhenNotProvided() {
        PingOptions options = PingOptions.builder()
                .status(RequestStatus.SUCCESS)
                .build();

        HeartbeatRequest request = options.toRequest();

        assertThat(request.getStatus()).isEqualTo(RequestStatus.SUCCESS);
        assertThat(request.getStartedAt()).isNotNull();
        assertThat(request.getCompletedAt()).isNotNull();
        // When neither provided, startedAt equals completedAt
        assertThat(request.getStartedAt()).isEqualTo(request.getCompletedAt());
    }

    @Test
    void shouldAutoSetStartedAtFromCompletedAtWhenOnlyCompletedAtProvided() {
        Instant completedAt = Instant.now();

        PingOptions options = PingOptions.builder()
                .status(RequestStatus.SUCCESS)
                .completedAt(completedAt)
                .build();

        HeartbeatRequest request = options.toRequest();

        assertThat(request.getCompletedAt()).isEqualTo(completedAt);
        assertThat(request.getStartedAt()).isEqualTo(completedAt);
    }

    @Test
    void shouldAutoSetCompletedAtForFailedStatus() {
        PingOptions options = PingOptions.builder()
                .status(RequestStatus.FAILED)
                .build();

        HeartbeatRequest request = options.toRequest();

        assertThat(request.getStatus()).isEqualTo(RequestStatus.FAILED);
        assertThat(request.getStartedAt()).isNotNull();
        assertThat(request.getCompletedAt()).isNotNull();
    }

    @Test
    void shouldSupportAllRequestStatusValues() {
        // All 3 RequestStatus values should work
        PingOptions successOptions = PingOptions.builder().status(RequestStatus.SUCCESS).build();
        assertThat(successOptions.getStatus()).isEqualTo(RequestStatus.SUCCESS);

        PingOptions failedOptions = PingOptions.builder().status(RequestStatus.FAILED).build();
        assertThat(failedOptions.getStatus()).isEqualTo(RequestStatus.FAILED);

        PingOptions runningOptions = PingOptions.builder().status(RequestStatus.RUNNING).build();
        assertThat(runningOptions.getStatus()).isEqualTo(RequestStatus.RUNNING);
    }
}
