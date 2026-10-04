package com.kebabshop.backend.menu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MenuItemImageUrlsTests {
    @ParameterizedTest
    @ValueSource(strings = {
            "http://images.example.com/image.jpg", "javascript:alert(1)",
            "data:image/png;base64,AAAA", "file:///image.png", "blob:https://example.com/id",
            "ftp://example.com/image.png", "//example.com/image.png", "/image.png",
            "https://", "https://bad host/image.png", "https://user:password@example.com/image.png",
            "https://user@example.com/image.png", "https://example.com:99999/image.png",
            "https://example.com\\@other.example/image.png"
    })
    void rejectsInsecureOrMalformedReferences(String value) {
        assertThrows(IllegalArgumentException.class, () -> MenuItemImageUrls.normalize(value));
    }

    @Test
    void preservesHttpsIncludingExistingManagedImagesAndAllowsClearing() {
        String cloudinary = "https://res.cloudinary.com/demo/image/upload/c_limit,h_1200,w_1200,q_auto:good,f_auto/gios-kebab/menu/id";
        assertEquals(cloudinary, MenuItemImageUrls.normalize(cloudinary));
        assertEquals("https://images.example.com/image.jpg?version=1", MenuItemImageUrls.normalize("  https://images.example.com/image.jpg?version=1  "));
        assertNull(MenuItemImageUrls.normalize(null));
        assertNull(MenuItemImageUrls.normalize("   "));
        assertThrows(IllegalArgumentException.class, () -> MenuItemImageUrls.normalize("https://example.com/" + "a".repeat(2048)));
    }
}
