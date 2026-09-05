package dev.akarshmi.UniTrace.modules.utils.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import dev.akarshmi.UniTrace.modules.exceptions.customExceptions.ImageUploadException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class CloudinaryService {

    private static final String FOLDER = "unitrace/items";

    private final Cloudinary cloudinary;
    private final ImageFileValidator imageFileValidator;

    public UploadResult upload(MultipartFile file) {
        if (!imageFileValidator.isValidImage(file)) {
            throw new IllegalArgumentException("Only JPEG, PNG, GIF, or WEBP images are allowed.");
        }

        try {
            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap("folder", FOLDER, "resource_type", "image")
            );
            return new UploadResult((String) result.get("secure_url"), (String) result.get("public_id"));
        } catch (IOException e) {
            log.error("Failed to upload image to Cloudinary", e);
            throw new ImageUploadException();
        }
    }

    public void delete(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (IOException e) {
            log.error("Failed to delete image {} from Cloudinary", publicId, e);
            // don't rethrow — a failed cleanup shouldn't block whatever operation triggered it
        }
    }

    public record UploadResult(String url, String publicId) {}

}

