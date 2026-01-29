package io.bearwatch.sdk.options;

import java.util.HashMap;
import java.util.Map;

/**
 * Options for the wrap() operation.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * WrapOptions options = WrapOptions.builder()
 *     .metadata("server", "backup-01")
 *     .metadata("region", "ap-northeast-2")
 *     .metadata("version", "1.2.0")
 *     .build();
 *
 * bw.wrap("my-job", options, () -> {
 *     // task logic
 * });
 * }</pre>
 */
public class WrapOptions {

    private final String output;
    private final Map<String, Object> metadata;

    private WrapOptions(Builder builder) {
        this.output = builder.output;
        this.metadata = builder.metadata.isEmpty() ? null : new HashMap<>(builder.metadata);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getOutput() {
        return output;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public static class Builder {
        private String output;
        private final Map<String, Object> metadata = new HashMap<>();

        private Builder() {}

        /**
         * Sets the job output message.
         *
         * @param output the output message (max 10KB)
         * @return this builder
         */
        public Builder output(String output) {
            this.output = output;
            return this;
        }

        /**
         * Adds a metadata entry.
         *
         * @param key the metadata key
         * @param value the metadata value
         * @return this builder
         */
        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        /**
         * Sets all metadata entries.
         *
         * @param metadata the metadata map
         * @return this builder
         */
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata.clear();
            this.metadata.putAll(metadata);
            return this;
        }

        public WrapOptions build() {
            return new WrapOptions(this);
        }
    }
}
