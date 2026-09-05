package dev.akarshmi.UniTrace.modules.auth.entity;

import dev.akarshmi.UniTrace.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "refresh_tokens",
        indexes = {
                @Index(name = "idx_refresh_tokens_user_id", columnList = "user_id"),
                @Index(name = "idx_refresh_tokens_expires_at", columnList = "expires_at"),
                @Index(name = "idx_refresh_tokens_user_revoked", columnList = "user_id, revoked")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "jti", nullable = false, unique = true, updatable = false, length = 36)
    private String jti;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "device_fingerprint", length = 128)
    private String deviceFingerprint;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_jti", length = 36)
    private String replacedByJti;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }


    public void revoke() {
        if (!this.revoked) {
            this.revoked = true;
            this.revokedAt = Instant.now();
        }
    }

    public void revokeAndReplace(String replacementJti) {
        revoke();
        this.replacedByJti = replacementJti;
    }

    public void markUsed() {
        this.lastUsedAt = Instant.now();
    }


    public boolean isExpired() {
        return !expiresAt.isAfter(Instant.now());
    }

    public boolean isValid() {
        return !revoked && !isExpired();
    }

    public boolean matchesDevice(String fingerprint) {
        return deviceFingerprint != null && deviceFingerprint.equals(fingerprint);
    }
}