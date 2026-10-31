package dev.akarshmi.UniTrace.modules.configs;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@Configuration
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties({EmbeddingProperties.class, EmbeddingJobProperties.class,
        MatchingProperties.class, DeskProperties.class})
public class MatchingConfig {

    /** Blocking (MVC-friendly) client with explicit timeouts for the embedding service. */
    @Bean("embeddingRestClient")
    RestClient embeddingRestClient(EmbeddingProperties props) {

        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(props.connectTimeout())
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(props.readTimeout());

        RestClient.Builder b = RestClient.builder().baseUrl(props.baseUrl()).requestFactory(factory);
        if (props.apiKey() != null && !props.apiKey().isBlank()) {
            b.defaultHeader("X-API-Key", props.apiKey());
        }
        return b.build();
    }
}
