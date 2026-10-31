package dev.akarshmi.UniTrace.modules.item.service;

import dev.akarshmi.UniTrace.modules.exceptions.EmbeddingException;

/**
 * Produces embeddings through the external embedding service.
 * Text: MiniLM (384-d). Images: CLIP (512-d). Text and image vectors live in different spaces and are never compared.
 */
public interface EmbeddingService {

    /** @throws EmbeddingException (transient or permanent) */
    float[] embedText(String text);

    /** The service downloads the image itself (HTTPS, allow-listed host). @throws EmbeddingException */
    float[] embedImage(String imageUrl);
}
