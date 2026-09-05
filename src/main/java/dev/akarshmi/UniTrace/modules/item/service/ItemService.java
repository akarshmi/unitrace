package dev.akarshmi.UniTrace.modules.item.service;

import dev.akarshmi.UniTrace.modules.item.dto.CreateItemRequest;
import dev.akarshmi.UniTrace.modules.item.dto.ItemResponse;
import dev.akarshmi.UniTrace.modules.item.dto.UpdateItemStatusRequest;
import dev.akarshmi.UniTrace.modules.item.entity.ItemStatus;
import dev.akarshmi.UniTrace.modules.item.entity.ItemType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public interface ItemService {
    ItemResponse create(CreateItemRequest itemRequest, HttpServletRequest request);

    ItemResponse getById(UUID id);

    Page<ItemResponse> list(ItemType type, ItemStatus status, Pageable pageable);

    ItemResponse updateStatus(UUID id, UpdateItemStatusRequest updateRequest, HttpServletRequest request);
}
