package dev.akarshmi.UniTrace.modules.item.dto;

import dev.akarshmi.UniTrace.modules.item.entity.ItemType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;

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

    /** When the item was lost/found (ISO-8601 instant). Optional; either bound may be omitted. */
    @PastOrPresent(message = "eventFrom cannot be in the future")
    private Instant eventFrom;

    @PastOrPresent(message = "eventTo cannot be in the future")
    private Instant eventTo;

    @NotNull(message = "Image is required")
    private MultipartFile image;

    @Valid
    GeoLocation geoLocation;

    @AssertTrue(message = "eventFrom must not be after eventTo")
    public boolean isEventWindowValid() {
        return eventFrom == null || eventTo == null || !eventFrom.isAfter(eventTo);
    }
}
