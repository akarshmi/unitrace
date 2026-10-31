package dev.akarshmi.UniTrace.modules.item.service.serviceImpl;

import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import dev.akarshmi.UniTrace.modules.configs.EmbeddingProperties;
import dev.akarshmi.UniTrace.modules.exceptions.EmbeddingException;
import dev.akarshmi.UniTrace.modules.item.service.EmbeddingService;
import dev.akarshmi.UniTrace.modules.item.util.VectorUtils;

import static org.springframework.data.redis.connection.ReactiveStreamCommands.AddStreamRecord.body;

@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    public record EmbedResponse(float[] embedding) {
    }

    private final RestClient client;
    private final EmbeddingProperties props;

    public EmbeddingServiceImpl(@Qualifier("embeddingRestClient") RestClient client, EmbeddingProperties props) {
        this.client = client;
        this.props = props;
    }

    @Override
    public float[] embedText(String text) {
        if (text == null || text.isBlank()) {
            throw EmbeddingException.permanent("Text to embed must not be blank");
        }
        float[] v = call("/embed/text", Map.of("text", text.strip()));
        return validated(v, props.textDimension(), "text");
    }

    @Override
    public float[] embedImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw EmbeddingException.permanent("Image URL must not be blank");
        }
        if (!imageUrl.regionMatches(true, 0, "https://", 0, 8)) {
            throw EmbeddingException.permanent("Image URL must use https");
        }
        float[] v = call("/embed/image", Map.of("image_url", imageUrl.strip()));
        return validated(v, props.imageDimension(), "image");
    }

    private float[] call(String path, Map<String, String> body) {
        try {
            EmbedResponse response = client.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(EmbedResponse.class);
            if (response == null || response.embedding() == null) {
                throw EmbeddingException.transientError("Embedding service returned an empty response", null);
            }
            return response.embedding();
        }
     catch (HttpClientErrorException e) {
        int status = e.getStatusCode().value();
        String detail = body(e);
        if (status == 408 || status == 429) {
            throw EmbeddingException.transientError("Embedding service busy (HTTP " + status + "): " + detail, e);
        }
        throw EmbeddingException.permanent(
                "Embedding service rejected the request (HTTP " + status + "): " + detail, e);
    } catch (HttpServerErrorException e) {
        throw EmbeddingException.transientError(
                "Embedding service error (HTTP " + e.getStatusCode().value() + "): " + body(e), e);
    }

        catch (ResourceAccessException e) {
            throw EmbeddingException.transientError("Embedding service unreachable or timed out", e);
        } catch (RestClientException e) {
            throw EmbeddingException.transientError("Unreadable embedding service response", e);
        }
    }

    private static String body(RestClientResponseException e) {
        String b = e.getResponseBodyAsString();
        if (b == null || b.isBlank()) {
            return "<empty body>";
        }
        b = b.replaceAll("\\s+", " ");
        return b.length() > 300 ? b.substring(0, 300) : b;
    }

    private static float[] validated(float[] v, int dimension, String kind) {
        if (v.length != dimension) {
            // Wrong model / wrong config: retrying cannot fix it.
            throw EmbeddingException.permanent(
                    kind + " embedding has dimension " + v.length + " but " + dimension + " is configured");
        }
        if (!VectorUtils.isUsable(v)) {
            throw EmbeddingException.transientError(kind + " embedding contains NaN/Infinity or is all zeros", null);
        }
        return v;
    }
}
