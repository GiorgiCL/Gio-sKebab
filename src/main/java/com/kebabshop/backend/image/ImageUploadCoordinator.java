package com.kebabshop.backend.image;

import com.kebabshop.backend.image.ManagedImageStorage.ImageKind;
import com.kebabshop.backend.image.ManagedImageStorage.StoredImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.function.Function;
import java.util.function.Supplier;

@Service
public class ImageUploadCoordinator {
    private static final Logger log = LoggerFactory.getLogger(ImageUploadCoordinator.class);
    private final ImageUpload validation;
    private final ManagedImageStorage storage;

    public ImageUploadCoordinator(ImageUpload validation, ManagedImageStorage storage) {
        this.validation = validation;
        this.storage = storage;
    }

    public <T> T upload(ImageKind kind, MultipartFile file, Function<StoredImage, ImageChange<T>> persist) {
        ImageUpload.ValidatedImage image = validation.validate(file);
        StoredImage stored = storage.upload(kind, image.bytes(), image.format());
        ImageChange<T> change;
        try {
            change = persist.apply(stored);
        } catch (RuntimeException failure) {
            cleanup(kind, stored.publicId());
            throw failure;
        }
        cleanup(kind, change.oldPublicId());
        return change.resource();
    }

    public <T> T remove(ImageKind kind, Supplier<ImageChange<T>> persist) {
        ImageChange<T> change = persist.get();
        cleanup(kind, change.oldPublicId());
        return change.resource();
    }

    private void cleanup(ImageKind kind, String publicId) {
        if (publicId == null || !publicId.startsWith(kind.folder() + "/")) return;
        try {
            storage.delete(kind, publicId);
        } catch (RuntimeException failure) {
            // The database now points at the replacement (or no image); cleanup can be retried manually.
            log.warn("Managed {} image cleanup failed", kind.name().toLowerCase());
        }
    }

    public record ImageChange<T>(T resource, String oldPublicId) {}
}
