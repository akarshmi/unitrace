package dev.akarshmi.UniTrace.modules.item.dto;

import dev.akarshmi.UniTrace.modules.item.entity.ItemType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

    @Getter
    @Setter
    public class CreateItemRequest {

        @NotNull(message = "Type is required")
        private ItemType type;

        @NotBlank(message = "Title is required")
        @Size(max = 150)
        private String title;

        @NotBlank(message = "Description is required")
        @Size(max = 1000)
        private String description;

        @NotBlank(message = "Location is required")
        @Size(max = 255)
        private String location;

        @NotNull(message = "Image is required")
        private MultipartFile image;

        @Valid
        GeoLocation geoLocation;
    }