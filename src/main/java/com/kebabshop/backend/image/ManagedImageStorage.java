package com.kebabshop.backend.image;

public interface ManagedImageStorage {
    StoredImage upload(ImageKind kind, byte[] bytes, String format);
    void delete(ImageKind kind, String publicId);

    enum ImageKind {
        MENU("gios-kebab/menu"), LUNCH("gios-kebab/lunch");
        private final String folder;
        ImageKind(String folder) { this.folder = folder; }
        public String folder() { return folder; }
    }

    record StoredImage(String url, String publicId) {}
}
