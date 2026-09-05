package dev.akarshmi.UniTrace.modules.item.controller;

import dev.akarshmi.UniTrace.modules.auth.security.JwtService;
import dev.akarshmi.UniTrace.modules.item.dto.CreateItemRequest;
import dev.akarshmi.UniTrace.modules.item.dto.ItemResponse;
import dev.akarshmi.UniTrace.modules.item.dto.UpdateItemStatusRequest;
import dev.akarshmi.UniTrace.modules.item.entity.ItemStatus;
import dev.akarshmi.UniTrace.modules.item.entity.ItemType;
import dev.akarshmi.UniTrace.modules.item.repository.ItemRepository;
import dev.akarshmi.UniTrace.modules.item.service.ItemService;
import dev.akarshmi.UniTrace.modules.user.repository.UserRepository;
import dev.akarshmi.UniTrace.modules.utils.constants.AppConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(AppConstants.BASE_URL+"/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ItemResponse> create(@ModelAttribute @Valid CreateItemRequest itemRequest, HttpServletRequest request ){
        return ResponseEntity.status(HttpStatus.CREATED).body(itemService.create(itemRequest, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ItemResponse> getById(@PathVariable UUID id) {
        System.out.println("getById");
        return ResponseEntity.ok(itemService.getById(id));
    }

    @GetMapping
    public ResponseEntity<Page<ItemResponse>> list(
            @RequestParam ItemType type,
            @RequestParam ItemStatus status,
            Pageable pageable
            ){
        return ResponseEntity.ok(itemService.list(type,status,pageable));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ItemResponse> updateStatus(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateItemStatusRequest updateRequest,
            HttpServletRequest request) {
        return ResponseEntity.ok(itemService.updateStatus(id,  updateRequest,request));
    }

}
