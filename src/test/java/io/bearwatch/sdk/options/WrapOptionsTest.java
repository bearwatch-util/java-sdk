package io.bearwatch.sdk.options;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WrapOptionsTest {

    @Test
    void shouldBuildWithDefaults() {
        WrapOptions options = WrapOptions.builder().build();

        assertThat(options.getOutput()).isNull();
        assertThat(options.getMetadata()).isNull();
    }

    @Test
    void shouldBuildWithOutput() {
        WrapOptions options = WrapOptions.builder()
                .output("Processed 100 records")
                .build();

        assertThat(options.getOutput()).isEqualTo("Processed 100 records");
        assertThat(options.getMetadata()).isNull();
    }

    @Test
    void shouldBuildWithMetadata() {
        WrapOptions options = WrapOptions.builder()
                .metadata("recordCount", 100)
                .metadata("status", "complete")
                .build();

        assertThat(options.getOutput()).isNull();
        assertThat(options.getMetadata()).containsEntry("recordCount", 100);
        assertThat(options.getMetadata()).containsEntry("status", "complete");
    }

    @Test
    void shouldBuildWithAllValues() {
        WrapOptions options = WrapOptions.builder()
                .output("Processed 100 records")
                .metadata("recordCount", 100)
                .metadata("status", "complete")
                .build();

        assertThat(options.getOutput()).isEqualTo("Processed 100 records");
        assertThat(options.getMetadata()).containsEntry("recordCount", 100);
        assertThat(options.getMetadata()).containsEntry("status", "complete");
    }

    @Test
    void shouldSetMetadataFromMap() {
        Map<String, Object> metadata = Map.of("key1", "value1", "key2", 123);

        WrapOptions options = WrapOptions.builder()
                .metadata(metadata)
                .build();

        assertThat(options.getMetadata()).hasSize(2);
        assertThat(options.getMetadata()).containsEntry("key1", "value1");
        assertThat(options.getMetadata()).containsEntry("key2", 123);
    }

    @Test
    void shouldOverrideMetadataWhenUsingMapMethod() {
        WrapOptions options = WrapOptions.builder()
                .metadata("initial", "value")
                .metadata(Map.of("key1", "value1"))
                .build();

        assertThat(options.getMetadata()).hasSize(1);
        assertThat(options.getMetadata()).containsEntry("key1", "value1");
        assertThat(options.getMetadata()).doesNotContainKey("initial");
    }
}
