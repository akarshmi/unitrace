package dev.akarshmi.UniTrace.modules.item.entity;

import dev.akarshmi.UniTrace.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "items",
        indexes = {
                @Index(name = "idx_items_type_status", columnList = "type, status"),
                @Index(name = "idx_items_reported_by", columnList = "reported_by")
        }
)
public class Item {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ItemType type;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private ItemStatus status = ItemStatus.OPEN;

    @Column(nullable = false, length = 255)
    private String location;

    // when the item was lost / found (optional; time score is neutral while null)
    @Column(name = "event_from")
    private Instant eventFrom;

    @Column(name = "event_to")
    private Instant eventTo;

    // images uploading
    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "image_public_id")
    private String imagePublicId;

    // ---- AI embeddings (written by ItemEmbeddingJob through native queries) ----
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 384)
    @Column(name = "text_embedding")
    private float[] textEmbedding;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 512)
    @Column(name = "image_embedding")
    private float[] imageEmbedding;

    @Enumerated(EnumType.STRING)
    @Column(name = "embedding_status", length = 15)
    @Builder.Default
    private EmbeddingStatus embeddingStatus = EmbeddingStatus.PENDING;

    @Column(name = "embedding_attempts")
    private Integer embeddingAttempts;

    @Column(name = "embedding_next_attempt_at")
    private Instant embeddingNextAttemptAt;

    @Column(name = "embedded_at")
    private Instant embeddedAt;

    @Column(name = "embedding_error", length = 500)
    private String embeddingError;

    // ---- physical drop-off at the Security Office / Lost & Found desk ----
    @Column(name = "desk_received_at")
    private Instant deskReceivedAt;

    @Column(name = "desk_received_by")
    private UUID deskReceivedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_by", nullable = false, updatable = false)
    private User reportedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void markMatched() {
        this.status = ItemStatus.MATCHED;
    }

    public void markClaimed() {
        this.status = ItemStatus.CLAIMED;
    }

    public void close() {
        this.status = ItemStatus.CLOSED;
    }

    public void markReceivedAtDesk(Instant at, UUID moderatorId) {
        this.deskReceivedAt = at;
        this.deskReceivedBy = moderatorId;
    }
}
