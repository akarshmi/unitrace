package dev.akarshmi.UniTrace.modules.matching.entity;

/** Lifecycle of an AI-detected match (moderation by the Lost & Found Desk is Phase 5). */
public enum MatchStatus {
    /** Detected by the AI, waiting for a moderator. */
    PENDING,
    /** Moderator confirmed it; students may be notified. */
    APPROVED,
    /** Moderator rejected it. */
    REJECTED,
    /** Item handed over at the desk after identity verification. */
    COMPLETED,
    /** Timed out without being resolved. */
    EXPIRED
}
