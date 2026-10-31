package dev.akarshmi.UniTrace.modules.item.service.serviceImpl;


import dev.akarshmi.UniTrace.modules.auth.security.JwtService;
import dev.akarshmi.UniTrace.modules.exceptions.customExceptions.ItemNotFoundException;
import dev.akarshmi.UniTrace.modules.exceptions.customExceptions.NotItemOwnerException;
import dev.akarshmi.UniTrace.modules.item.dto.CreateItemRequest;
import dev.akarshmi.UniTrace.modules.item.dto.ItemResponse;
import dev.akarshmi.UniTrace.modules.item.dto.UpdateItemStatusRequest;
import dev.akarshmi.UniTrace.modules.item.entity.Item;
import dev.akarshmi.UniTrace.modules.item.entity.ItemStatus;
import dev.akarshmi.UniTrace.modules.item.entity.ItemType;
import dev.akarshmi.UniTrace.modules.item.event.ItemCreatedEvent;
import dev.akarshmi.UniTrace.modules.item.repository.ItemRepository;
import dev.akarshmi.UniTrace.modules.item.service.ItemService;
import dev.akarshmi.UniTrace.modules.user.entity.User;
import dev.akarshmi.UniTrace.modules.user.repository.UserRepository;
import dev.akarshmi.UniTrace.modules.utils.services.CloudinaryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {


    private final ItemRepository itemRepository;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;
    private final ApplicationEventPublisher events;


    @Override
    @Transactional
    public ItemResponse create(CreateItemRequest itemRequest, HttpServletRequest request) {

        //first extract the reporter
        String header = request.getHeader("Authorization");
        String token = header.substring(7);
        UUID userId = jwtService.getUserId(token);

        User reporter = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Authenticated user not found."));

        if (itemRequest.getEventFrom() != null && itemRequest.getEventTo() != null
                && itemRequest.getEventFrom().isAfter(itemRequest.getEventTo())) {
            throw new IllegalArgumentException("eventFrom must not be after eventTo.");
        }

        //submit the request first then create
        CloudinaryService.UploadResult upload = cloudinaryService.upload(itemRequest.getImage());

        Item item = Item.builder()
                .type(itemRequest.getType())
                .title(itemRequest.getTitle())
                .description(itemRequest.getDescription())
                .location(itemRequest.getLocation())
                .eventFrom(itemRequest.getEventFrom())
                .eventTo(itemRequest.getEventTo())
                .imageUrl(upload.url())
                .imagePublicId(upload.publicId())
                .reportedBy(reporter)
                .build();

        Item saved = itemRepository.save(item);

        // After commit, ItemEmbeddingJob embeds this item and then runs matching.
        events.publishEvent(new ItemCreatedEvent(saved.getId()));
        return ItemResponse.from(saved);
    }

    @Override
    public ItemResponse getById(UUID id) {
        Item item = itemRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Item not found."));
        return ItemResponse.from(item);
    }

    @Override
    public Page<ItemResponse> list(ItemType type, ItemStatus status, Pageable pageable) {
        Page<Item> items = itemRepository.findByTypeAndStatus(type, status, pageable);
        return items.map(ItemResponse::from);
    }

    @Override
    @Transactional
    public ItemResponse updateStatus(UUID id, UpdateItemStatusRequest updateRequest, HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        String token = header.substring(7);
        UUID requesterId = jwtService.getUserId(token);


        Item item = itemRepository.findById(id).orElseThrow(ItemNotFoundException::new);

        if (!item.getReportedBy().getId().equals(requesterId)) {
            throw new NotItemOwnerException();
        }

        switch (updateRequest.status()) {
            case MATCHED -> item.markMatched();
            case CLAIMED -> item.markClaimed();
            case CLOSED -> item.close();
            case OPEN -> throw new IllegalArgumentException("Cannot manually revert an item to OPEN.");
        }

        return ItemResponse.from(itemRepository.save(item));
    }

}
