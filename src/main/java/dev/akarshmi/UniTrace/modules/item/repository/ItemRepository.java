package dev.akarshmi.UniTrace.modules.item.repository;

import dev.akarshmi.UniTrace.modules.item.entity.Item;
import dev.akarshmi.UniTrace.modules.item.entity.ItemStatus;
import dev.akarshmi.UniTrace.modules.item.entity.ItemType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ItemRepository extends JpaRepository<Item, UUID> {
    Page<Item> findByTypeAndStatus(ItemType type, ItemStatus status, Pageable pageable);
    Page<Item> findByReportedById(UUID reportedById, Pageable pageable);

}
