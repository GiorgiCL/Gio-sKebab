package com.kebabshop.backend.image;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.api.exceptions.ApiException;
import com.cloudinary.api.exceptions.BadRequest;
import com.cloudinary.api.exceptions.NotAllowed;
import com.cloudinary.api.exceptions.RateLimited;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;

@Component
class CloudinaryImageStorage implements ManagedImageStorage {
    private static final Logger log = LoggerFactory.getLogger(CloudinaryImageStorage.class);
    private final Environment environment;

    CloudinaryImageStorage(Environment environment) { this.environment = environment; }

    @Override
    public StoredImage upload(ImageKind kind, byte[] bytes, String format) {
        Cloudinary cloudinary = configuredCloudinary();
        String publicId;
        String uploadedPublicId = null;
        try {
            Map<String, Object> options = uploadOptions(kind, UUID.randomUUID().toString());
            Map<?, ?> uploaded = cloudinary.uploader().upload(bytes, options);
            Object returnedPublicId = uploaded.get("public_id");
            if (returnedPublicId instanceof String id) uploadedPublicId = id;
            if (!(returnedPublicId instanceof String id) || !id.startsWith(kind.folder() + "/")) {
                throw new IllegalStateException("Cloudinary returned an invalid managed image identifier");
            }
            publicId = id;
            String url = cloudinary.url().secure(true)
                    .transformation(new Transformation().width(1200).height(1200).crop("limit")
                            .quality("auto:good").fetchFormat("auto"))
                    .generate(publicId);
            return new StoredImage(url, publicId);
        } catch (Exception exception) {
            logFailure("upload", kind, exception);
            if (uploadedPublicId != null) {
                try { cloudinary.uploader().destroy(uploadedPublicId, Map.of("resource_type", "image")); }
                catch (Exception cleanupFailure) { logFailure("cleanup_after_upload", kind, cleanupFailure); }
            }
            throw new ImageStorageException("Image upload failed");
        }
    }

    @Override
    public void delete(ImageKind kind, String publicId) {
        if (publicId == null || !publicId.startsWith(kind.folder() + "/")) return;
        try {
            configuredCloudinary().uploader().destroy(publicId, Map.of("resource_type", "image", "invalidate", true));
        } catch (Exception exception) {
            logFailure("delete", kind, exception);
            throw new ImageStorageException("Image cleanup failed");
        }
    }

    private Cloudinary configuredCloudinary() {
        String cloudName = environment.getProperty("CLOUDINARY_CLOUD_NAME");
        String apiKey = environment.getProperty("CLOUDINARY_API_KEY");
        String apiSecret = environment.getProperty("CLOUDINARY_API_SECRET");
        if (blank(cloudName) || blank(apiKey) || blank(apiSecret)) {
            log.warn("Cloudinary image configuration missing; cloudNamePresent={} apiKeyPresent={} apiSecretPresent={}",
                    !blank(cloudName), !blank(apiKey), !blank(apiSecret));
            throw new ImageStorageException("Image uploads are temporarily unavailable");
        }
        return new Cloudinary(Map.of("cloud_name", cloudName, "api_key", apiKey, "api_secret", apiSecret));
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    static Map<String, Object> uploadOptions(ImageKind kind, String randomId) {
        Map<String, Object> options = new HashMap<>();
        // An explicit public ID path works in both Cloudinary folder modes and keeps deletion scoped.
        options.put("public_id", kind.folder() + "/" + randomId);
        options.put("asset_folder", kind.folder());
        options.put("overwrite", false);
        options.put("resource_type", "image");
        options.put("allowed_formats", List.of("jpg", "jpeg", "png", "webp", "heic", "heif"));
        return options;
    }

    private void logFailure(String operation, ImageKind kind, Exception exception) {
        // Cloudinary error bodies can include request data. Log only fixed labels, never the response or signed options.
        log.warn("Cloudinary image operation={} kind={} failure={} exception={}",
                operation, kind.name().toLowerCase(Locale.ROOT), classifyFailure(exception), exception.getClass().getSimpleName());
    }

    static String classifyFailure(Exception exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("invalid signature") || message.contains("signature mismatch")) return "INVALID_SIGNATURE";
        if (message.contains("invalid api key") || message.contains("unknown api key") || message.contains("api key not found")) return "INVALID_API_KEY";
        if (message.contains("invalid api secret")) return "INVALID_API_SECRET";
        if (message.contains("unauthorized") || message.contains("authentication failed")) return "AUTHENTICATION_FAILED";
        if (message.contains("not authorized") || message.contains("access denied") || message.contains("permission")
                || message.contains("not allowed") || message.contains("forbidden")) return "AUTHORIZATION_DENIED";
        if (exception instanceof NotAllowed) return "AUTHENTICATION_OR_AUTHORIZATION";
        if (exception instanceof BadRequest) return "INVALID_UPLOAD";
        if (exception instanceof RateLimited) return "UPSTREAM_RATE_LIMIT";
        if (exception instanceof ApiException) return "UPSTREAM_API_ERROR";
        if (exception instanceof java.io.IOException) return "NETWORK_ERROR";
        return "UPSTREAM_OR_SDK_ERROR";
    }

    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public static class ImageStorageException extends RuntimeException {
        ImageStorageException(String message) { super(message); }
    }
}
