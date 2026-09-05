package dev.akarshmi.UniTrace.modules.auth.repository;

import dev.akarshmi.UniTrace.modules.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByJti(String jti);
    void deleteByUserId(UUID userId);
}
