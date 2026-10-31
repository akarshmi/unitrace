package dev.akarshmi.UniTrace.modules.matching.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;

import dev.akarshmi.UniTrace.modules.exceptions.customExceptions.InvalidMatchStateException;
import dev.akarshmi.UniTrace.modules.item.entity.Item;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/**
 * A candidate pairing of one LOST and one FOUND item (each pair exists at most once).
 * Lifecycle: PENDING -> APPROVED -> COMPLETED, or PENDING -> REJECTED, or PENDING -> EXPIRED (superseded).
 */
@Entity
@Table(name = "matches",
        uniqueConstraints = @UniqueConstraint(name = "uk_match_pair", columnNames = {"lost_item_id", "found_item_id"}),
        indexes = {
                @Index(name = "idx_match_status_score", columnList = "status, final_score"),
                @Index(name = "idx_match_lost_item", columnList = "lost_item_id"),
                @Index(name = "idx_match_found_item", columnList = "found_item_id")
        })
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lost_item_id", nullable = false)
    private Item lostItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "found_item_id", nullable = false)
    private Item foundItem;

    /** 1 - cosine distance of the text embeddings, in [0,1]. */
    @Column(name = "text_score", nullable = false)
    private double textScore;

    /** False when either item had no image embedding; imageScore then holds the neutral fallback. */
    @Column(name = "image_compared", nullable = false)
    private boolean imageCompared;

    @Column(name = "image_score", nullable = false)
    private double imageScore;

    @Column(name = "time_score", nullable = false)
    private double timeScore;

    @Column(name = "final_score", nullable = false)
    private double finalScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MatchStatus status = MatchStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /** Moderator user id. */
    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    /** HMAC of the handover key; the plain key exists only in the e-mail to the lost-item owner. */
    @Column(name = "handover_key_hash", length = 64)
    private String handoverKeyHash;

    @Column(name = "handover_key_expires_at")
    private Instant handoverKeyExpiresAt;

    @ColumnDefault("0")
    @Column(name = "handover_failed_attempts", nullable = false)
    private int handoverFailedAttempts;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by")
    private UUID completedBy;

    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = MatchStatus.PENDING;
        }
    }

    // ------------------------------------------------------------------ lifecycle

    public void requireStatus(MatchStatus expected) {
        if (status != expected) {
            throw new InvalidMatchStateException(
                    "Match is " + status + ", expected " + expected + ".");
        }
    }

    public void approve(UUID moderatorId, String note, String keyHash, Instant now, Instant keyExpiresAt) {
        requireStatus(MatchStatus.PENDING);
        this.status = MatchStatus.APPROVED;
        this.reviewedBy = moderatorId;
        this.reviewedAt = now;
        this.reviewNote = note;
        issueKey(keyHash, keyExpiresAt);
    }

    public void reject(UUID moderatorId, String note, Instant now) {
        requireStatus(MatchStatus.PENDING);
        this.status = MatchStatus.REJECTED;
        this.reviewedBy = moderatorId;
        this.reviewedAt = now;
        this.reviewNote = note;
    }

    public void reissueKey(String keyHash, Instant keyExpiresAt) {
        requireStatus(MatchStatus.APPROVED);
        issueKey(keyHash, keyExpiresAt);
    }

    private void issueKey(String keyHash, Instant keyExpiresAt) {
        this.handoverKeyHash = keyHash;
        this.handoverKeyExpiresAt = keyExpiresAt;
        this.handoverFailedAttempts = 0;
    }

    public void recordFailedKeyAttempt() {
        this.handoverFailedAttempts++;
    }

    public boolean isKeyLocked(int maxAttempts) {
        return handoverFailedAttempts >= maxAttempts;
    }

    public void complete(UUID moderatorId, Instant now) {
        requireStatus(MatchStatus.APPROVED);
        this.status = MatchStatus.COMPLETED;
        this.completedAt = now;
        this.completedBy = moderatorId;
        this.handoverKeyHash = null; // one-time use
    }

    // ------------------------------------------------------------------ accessors

    public UUID getId() { return id; }
    public Item getLostItem() { return lostItem; }
    public void setLostItem(Item lostItem) { this.lostItem = lostItem; }
    public Item getFoundItem() { return foundItem; }
    public void setFoundItem(Item foundItem) { this.foundItem = foundItem; }
    public double getTextScore() { return textScore; }
    public void setTextScore(double textScore) { this.textScore = textScore; }
    public boolean isImageCompared() { return imageCompared; }
    public void setImageCompared(boolean imageCompared) { this.imageCompared = imageCompared; }
    public double getImageScore() { return imageScore; }
    public void setImageScore(double imageScore) { this.imageScore = imageScore; }
    public double getTimeScore() { return timeScore; }
    public void setTimeScore(double timeScore) { this.timeScore = timeScore; }
    public double getFinalScore() { return finalScore; }
    public void setFinalScore(double finalScore) { this.finalScore = finalScore; }
    public MatchStatus getStatus() { return status; }
    public void setStatus(MatchStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public UUID getReviewedBy() { return reviewedBy; }
    public String getReviewNote() { return reviewNote; }
    public String getHandoverKeyHash() { return handoverKeyHash; }
    public Instant getHandoverKeyExpiresAt() { return handoverKeyExpiresAt; }
    public int getHandoverFailedAttempts() { return handoverFailedAttempts; }
    public Instant getCompletedAt() { return completedAt; }
    public UUID getCompletedBy() { return completedBy; }
    public Long getVersion() { return version; }
}
