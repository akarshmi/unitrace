package dev.akarshmi.UniTrace.modules.item.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import dev.akarshmi.UniTrace.modules.item.entity.Item;

/**
 * Native pgvector / embedding-queue operations. Kept separate from ItemRepository so the working item module is
 * untouched. Vectors are passed as pgvector text literals ("[0.1,0.2,...]") and cast in SQL.
 */
public interface ItemVectorRepository extends Repository<Item, UUID> {

    @Query(value = """
            SELECT i.id AS id,
                   (i.text_embedding <=> CAST(:vec AS vector)) AS distance
            FROM items i
            WHERE i.type = :type
              AND i.status IN (:statuses)
              AND i.text_embedding IS NOT NULL
              AND i.id <> :excludeId
            ORDER BY i.text_embedding <=> CAST(:vec AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<ItemDistanceProjection> findCandidateDistances(@Param("vec") String vectorLiteral,
                                                        @Param("type") String itemType,
                                                        @Param("statuses") Collection<String> statuses,
                                                        @Param("excludeId") UUID excludeId,
                                                        @Param("limit") int limit);

    /** Atomically leases up to :limit due items (SKIP LOCKED => safe with several app instances). */
    @Transactional
    @Query(value = """
            UPDATE items
               SET embedding_status = 'PROCESSING',
                   embedding_attempts = COALESCE(embedding_attempts, 0) + 1,
                   embedding_next_attempt_at = :leaseUntil
             WHERE id IN (
                   SELECT id FROM items
                    WHERE ((embedding_status IS NULL OR embedding_status IN ('PENDING', 'RETRY'))
                           AND (embedding_next_attempt_at IS NULL OR embedding_next_attempt_at <= :now))
                       OR (embedding_status = 'PROCESSING' AND embedding_next_attempt_at <= :now)
                    ORDER BY embedding_next_attempt_at NULLS FIRST
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED)
            RETURNING id
            """, nativeQuery = true)
    List<UUID> claimBatch(@Param("now") Instant now,
                          @Param("leaseUntil") Instant leaseUntil,
                          @Param("limit") int limit);

    /** Leases one specific item (used right after creation). Empty result => someone else owns it or it is done. */
    @Transactional
    @Query(value = """
            UPDATE items
               SET embedding_status = 'PROCESSING',
                   embedding_attempts = COALESCE(embedding_attempts, 0) + 1,
                   embedding_next_attempt_at = :leaseUntil
             WHERE id = :id
               AND (embedding_status IS NULL OR embedding_status IN ('PENDING', 'RETRY'))
               AND (embedding_next_attempt_at IS NULL OR embedding_next_attempt_at <= :now)
            RETURNING id
            """, nativeQuery = true)
    List<UUID> claimById(@Param("id") UUID id,
                         @Param("now") Instant now,
                         @Param("leaseUntil") Instant leaseUntil);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE items
               SET text_embedding = CAST(:text AS vector),
                   image_embedding = CAST(:image AS vector),
                   embedding_status = 'READY',
                   embedded_at = :now,
                   embedding_error = NULL,
                   embedding_next_attempt_at = NULL
             WHERE id = :id AND embedding_status = 'PROCESSING'
            """, nativeQuery = true)
    int markReady(@Param("id") UUID id,
                  @Param("text") String textLiteral,
                  @Param("image") String imageLiteral,
                  @Param("now") Instant now);

    /** Text-only variant: leaves any existing image_embedding untouched. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE items
               SET text_embedding = CAST(:text AS vector),
                   embedding_status = 'READY',
                   embedded_at = :now,
                   embedding_error = NULL,
                   embedding_next_attempt_at = NULL
             WHERE id = :id AND embedding_status = 'PROCESSING'
            """, nativeQuery = true)
    int markReadyTextOnly(@Param("id") UUID id,
                          @Param("text") String textLiteral,
                          @Param("now") Instant now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE items
               SET embedding_status = 'RETRY',
                   embedding_next_attempt_at = :nextAttemptAt,
                   embedding_error = :error
             WHERE id = :id AND embedding_status = 'PROCESSING'
            """, nativeQuery = true)
    int markRetry(@Param("id") UUID id,
                  @Param("nextAttemptAt") Instant nextAttemptAt,
                  @Param("error") String error);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE items
               SET embedding_status = 'FAILED',
                   embedding_next_attempt_at = NULL,
                   embedding_error = :error
             WHERE id = :id AND embedding_status = 'PROCESSING'
            """, nativeQuery = true)
    int markFailed(@Param("id") UUID id, @Param("error") String error);
}
