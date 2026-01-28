package io.bearwatch.sdk.options;

import java.util.HashMap;
import java.util.Map;

/**
 * Options for the complete() operation.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * CompleteOptions options = CompleteOptions.builder()
 *     .output("Processed 100 records")
 *     .metadata("recordCount", 100)
 *     .build();
 * }</pre>
 */
public class CompleteOptions {

    private final String output;
    private final Map<String, Object> metadata;

    private CompleteOptions(Builder builder) {
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

        public CompleteOptions build() {
            return new CompleteOptions(this);
        }
    }
}
