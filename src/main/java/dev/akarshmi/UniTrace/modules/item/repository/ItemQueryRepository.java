package dev.akarshmi.UniTrace.modules.item.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import dev.akarshmi.UniTrace.modules.item.entity.Item;

/** Extra read queries, kept apart from ItemRepository so that file stays untouched. */

public interface ItemQueryRepository extends Repository<Item, UUID> {

    Page<Item> findByReportedBy_Id(UUID userId, Pageable pageable);
}
