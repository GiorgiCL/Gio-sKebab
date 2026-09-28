package com.kebabshop.backend.image;

import com.kebabshop.backend.image.ManagedImageStorage.ImageKind;
import com.kebabshop.backend.image.ManagedImageStorage.StoredImage;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.core.env.Environment;
import com.cloudinary.api.exceptions.BadRequest;
import com.cloudinary.api.exceptions.NotAllowed;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImageUploadTests {
    private final ImageUpload validation = new ImageUpload();

    @Test
    void acceptsSupportedImageSignaturesWithoutTrustingMimeOrFilename() {
        assertEquals("jpg", validation.validate(file("photo.svg", "image/svg+xml", 0xff, 0xd8, 0xff)).format());
        assertEquals("png", validation.validate(file("anything.bin", "application/octet-stream",
                0x89, 'P', 'N', 'G', 13, 10, 26, 10)).format());
        assertEquals("webp", validation.validate(file("x", "text/plain", 'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P')).format());
        assertEquals("heic", validation.validate(file("phone.heic", "image/heic", 0, 0, 0, 0, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c')).format());
    }

    @Test
    void rejectsEmptySvgAndArbitraryFiles() {
        assertStatus(HttpStatus.BAD_REQUEST, new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]));
        assertStatus(HttpStatus.BAD_REQUEST, new MockMultipartFile("file", "vector.svg", "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes()));
        assertStatus(HttpStatus.BAD_REQUEST, new MockMultipartFile("file", "fake.jpg", "image/jpeg", new byte[]{1, 2, 3, 4}));
    }

    @Test
    void enforcesTenMegabyteLimit() {
        byte[] large = new byte[ImageUpload.MAX_BYTES + 1];
        assertStatus(HttpStatus.PAYLOAD_TOO_LARGE, new MockMultipartFile("file", "large.jpg", "image/jpeg", large));
    }

    @Test
    void uploadsThenPersistsThenCleansUpOldManagedAsset() {
        ManagedImageStorage storage = mock(ManagedImageStorage.class);
        when(storage.upload(eq(ImageKind.MENU), any(), eq("jpg")))
                .thenReturn(new StoredImage("https://res.cloudinary.com/demo/image/upload/f_auto/photo", "gios-kebab/menu/new"));
        ImageUploadCoordinator coordinator = new ImageUploadCoordinator(validation, storage);
        List<String> sequence = new ArrayList<>();
        doAnswer(call -> { sequence.add("delete:" + call.getArgument(1)); return null; })
                .when(storage).delete(any(), anyString());

        String result = coordinator.upload(ImageKind.MENU, file("photo.jpg", "image/jpeg", 0xff, 0xd8, 0xff), image -> {
            sequence.add("persist:" + image.publicId());
            return new ImageUploadCoordinator.ImageChange<>("updated", "gios-kebab/menu/old");
        });

        assertEquals("updated", result);
        assertEquals(List.of("persist:gios-kebab/menu/new", "delete:gios-kebab/menu/old"), sequence);
    }

    @Test
    void compensatesNewUploadWhenDatabaseUpdateFails() {
        ManagedImageStorage storage = mock(ManagedImageStorage.class);
        when(storage.upload(eq(ImageKind.LUNCH), any(), eq("png")))
                .thenReturn(new StoredImage("https://example.test/new", "gios-kebab/lunch/new"));
        ImageUploadCoordinator coordinator = new ImageUploadCoordinator(validation, storage);
        assertThrows(IllegalStateException.class, () -> coordinator.upload(ImageKind.LUNCH,
                file("meal.png", "image/png", 0x89, 'P', 'N', 'G', 13, 10, 26, 10), image -> {
                    throw new IllegalStateException("database unavailable");
                }));
        verify(storage).delete(ImageKind.LUNCH, "gios-kebab/lunch/new");
    }

    @Test
    void removalAndReplacementNeverDeleteAnExternalOrUnmanagedReference() {
        ManagedImageStorage storage = mock(ManagedImageStorage.class);
        ImageUploadCoordinator coordinator = new ImageUploadCoordinator(validation, storage);
        coordinator.remove(ImageKind.MENU, () -> new ImageUploadCoordinator.ImageChange<>("cleared", null));
        when(storage.upload(eq(ImageKind.MENU), any(), eq("jpg")))
                .thenReturn(new StoredImage("https://res.cloudinary.com/demo/new", "gios-kebab/menu/new"));
        coordinator.upload(ImageKind.MENU, file("x.jpg", "image/jpeg", 0xff, 0xd8, 0xff), image ->
                new ImageUploadCoordinator.ImageChange<>("replaced", "https://images.example/owner.jpg"));
        verify(storage, never()).delete(eq(ImageKind.MENU), eq("https://images.example/owner.jpg"));
        verify(storage, never()).delete(eq(ImageKind.MENU), isNull());
    }

    @Test
    void removalCleansUpOnlyThePersistedManagedAsset() {
        ManagedImageStorage storage = mock(ManagedImageStorage.class);
        ImageUploadCoordinator coordinator = new ImageUploadCoordinator(validation, storage);
        String result = coordinator.remove(ImageKind.LUNCH, () ->
                new ImageUploadCoordinator.ImageChange<>("cleared", "gios-kebab/lunch/managed-id"));
        assertEquals("cleared", result);
        verify(storage).delete(ImageKind.LUNCH, "gios-kebab/lunch/managed-id");
    }

    @Test
    void cloudinaryConfigurationIsRequiredOnlyWhenImageStorageIsUsed() {
        Environment environment = mock(Environment.class);
        CloudinaryImageStorage storage = new CloudinaryImageStorage(environment);
        CloudinaryImageStorage.ImageStorageException exception = assertThrows(
                CloudinaryImageStorage.ImageStorageException.class,
                () -> storage.upload(ImageKind.MENU, new byte[]{1}, "jpg"));
        assertFalse(exception.getMessage().contains("api_secret"));
    }

    @Test
    void cloudinaryFailuresAreClassifiedWithoutExposingResponseText() {
        assertEquals("INVALID_SIGNATURE", CloudinaryImageStorage.classifyFailure(new NotAllowed("Invalid Signature; signed request material")));
        assertEquals("INVALID_API_KEY", CloudinaryImageStorage.classifyFailure(new NotAllowed("Invalid API key")));
        assertEquals("AUTHORIZATION_DENIED", CloudinaryImageStorage.classifyFailure(new NotAllowed("Permission denied for asset folder")));
        assertEquals("INVALID_UPLOAD", CloudinaryImageStorage.classifyFailure(new BadRequest("Unsupported image format")));
        assertEquals("NETWORK_ERROR", CloudinaryImageStorage.classifyFailure(new java.io.IOException("Connection reset")));
    }

    @Test
    void cloudinaryUploadOptionsKeepPublicIdsInsideManagedNamespaces() {
        var menu = CloudinaryImageStorage.uploadOptions(ImageKind.MENU, "random-id");
        var lunch = CloudinaryImageStorage.uploadOptions(ImageKind.LUNCH, "random-id");
        assertEquals("gios-kebab/menu/random-id", menu.get("public_id"));
        assertEquals("gios-kebab/menu", menu.get("asset_folder"));
        assertEquals("gios-kebab/lunch/random-id", lunch.get("public_id"));
        assertEquals("gios-kebab/lunch", lunch.get("asset_folder"));
        assertFalse(menu.containsKey("public_id_prefix"));
    }

    private MockMultipartFile file(String name, String contentType, int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) bytes[i] = (byte) values[i];
        return new MockMultipartFile("file", name, contentType, bytes);
    }

    private void assertStatus(HttpStatus expected, MockMultipartFile file) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> validation.validate(file));
        assertEquals(expected, exception.getStatusCode());
    }
}
