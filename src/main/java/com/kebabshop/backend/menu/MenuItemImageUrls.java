package com.kebabshop.backend.menu;

import java.net.URI;

public final class MenuItemImageUrls {
    private MenuItemImageUrls() {}

    public static String normalize(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        String normalized = imageUrl.trim();
        if (normalized.length() > 2048) throw new IllegalArgumentException("Invalid menu item image URL");
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getPort() > 65535
                    || !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("Invalid menu item image URL");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid menu item image URL", exception);
        }
        return normalized;
    }
}
