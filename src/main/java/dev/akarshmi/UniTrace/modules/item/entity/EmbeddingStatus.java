package dev.akarshmi.UniTrace.modules.item.entity;

/** Lifecycle of embedding generation for one item. A NULL column value is treated as PENDING. */
public enum EmbeddingStatus {
    PENDING,
    PROCESSING,
    READY,
    RETRY,
    FAILED
}
