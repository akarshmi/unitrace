package dev.akarshmi.UniTrace.modules.matching.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import dev.akarshmi.UniTrace.modules.matching.entity.Match;
import dev.akarshmi.UniTrace.modules.matching.entity.MatchStatus;

public interface MatchRepository extends JpaRepository<Match, UUID> {

    boolean existsByLostItem_IdAndFoundItem_Id(UUID lostItemId, UUID foundItemId);

    List<Match> findByLostItem_IdOrFoundItem_Id(UUID lostItemId, UUID foundItemId);

    @EntityGraph(attributePaths = {"lostItem", "lostItem.reportedBy", "foundItem", "foundItem.reportedBy"})
    Page<Match> findByStatus(MatchStatus status, Pageable pageable);

    /** Match with both items and their reporters loaded (desk views, moderation). */
    @EntityGraph(attributePaths = {"lostItem", "lostItem.reportedBy", "foundItem", "foundItem.reportedBy"})
    @Query("select m from Match m where m.id = :id")
    Optional<Match> fetchById(@Param("id") UUID id);

    /** Matches a student may see (after moderation) in which they own the lost or the found report. */
    @EntityGraph(attributePaths = {"lostItem", "lostItem.reportedBy", "foundItem", "foundItem.reportedBy"})
    @Query("""
            select m from Match m
            where m.status in :statuses
              and (m.lostItem.reportedBy.id = :userId or m.foundItem.reportedBy.id = :userId)
            order by m.createdAt desc
            """)
    Page<Match> findVisibleForUser(@Param("userId") UUID userId,
                                   @Param("statuses") Collection<MatchStatus> statuses,
                                   Pageable pageable);

    /** After a handover, every other open match touching either item is moot. */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("""
            update Match m
               set m.status = :to, m.reviewedAt = :now, m.version = m.version + 1
             where m.status = :from
               and m.id <> :keep
               and (m.lostItem.id in :itemIds or m.foundItem.id in :itemIds)
            """)
    int supersede(@Param("keep") UUID keepId,
                  @Param("itemIds") Collection<UUID> itemIds,
                  @Param("from") MatchStatus from,
                  @Param("to") MatchStatus to,
                  @Param("now") Instant now);

    /**
     * Race-proof insert: relies on the unique (lost_item_id, found_item_id) index. Returns the new id, or an empty
     * list if the pair already existed. All parameters are non-null on purpose (avoids untyped NULL binding).
     */
    @Transactional
    @Query(value = """
            INSERT INTO matches (id, lost_item_id, found_item_id, text_score, image_compared, image_score,
                                 time_score, final_score, status, created_at, handover_failed_attempts, version)
            VALUES (gen_random_uuid(), :lostId, :foundId, :textScore, :imageCompared, :imageScore,
                    :timeScore, :finalScore, 'PENDING', now(), 0, 0)
            ON CONFLICT (lost_item_id, found_item_id) DO NOTHING
            RETURNING id
            """, nativeQuery = true)
    List<UUID> insertIfAbsent(@Param("lostId") UUID lostId,
                              @Param("foundId") UUID foundId,
                              @Param("textScore") double textScore,
                              @Param("imageCompared") boolean imageCompared,
                              @Param("imageScore") double imageScore,
                              @Param("timeScore") double timeScore,
                              @Param("finalScore") double finalScore);
}
