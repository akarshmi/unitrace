package dev.akarshmi.UniTrace.modules.configs;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Tuning for {@code ItemEmbeddingJob}. Poll interval is set via unitrace.embedding.job.poll-interval (ISO-8601). */
@ConfigurationProperties(prefix = "unitrace.embedding.job")
public record EmbeddingJobProperties(
        @DefaultValue("5") int batchSize,
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("10m") Duration leaseDuration,
        @DefaultValue("30s") Duration backoffBase,
        @DefaultValue("30m") Duration backoffMax) {

    public EmbeddingJobProperties {
        if (batchSize < 1 || maxAttempts < 1) {
            throw new IllegalArgumentException("batch-size and max-attempts must be >= 1");
        }
    }
}
