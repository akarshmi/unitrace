package dev.akarshmi.UniTrace.modules.configs;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Connection settings for the external embedding service (FastAPI app in /embedding-service). */
@ConfigurationProperties(prefix = "unitrace.embedding")
public record EmbeddingProperties(
        @DefaultValue("http://localhost:8000") String baseUrl,
        String apiKey,
        @DefaultValue("384") int textDimension,
        @DefaultValue("512") int imageDimension,
        @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue("30s") Duration readTimeout) {
}
