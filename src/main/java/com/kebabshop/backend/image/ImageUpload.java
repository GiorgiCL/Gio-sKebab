package com.kebabshop.backend.image;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

@Component
public class ImageUpload {
    public static final int MAX_BYTES = 10 * 1024 * 1024;

    public ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalid("Choose a non-empty image file");
        if (file.getSize() > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Image must be 10 MB or smaller");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw invalid("Could not read image file");
        }
        if (bytes.length == 0) throw invalid("Choose a non-empty image file");
        if (bytes.length > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Image must be 10 MB or smaller");

        String format = detectFormat(bytes);
        if (format == null) throw invalid("Supported images are JPEG, PNG, WebP, HEIC, and HEIF");
        return new ValidatedImage(bytes, format);
    }

    private String detectFormat(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 255) == 0xff && (bytes[1] & 255) == 0xd8 && (bytes[2] & 255) == 0xff) return "jpg";
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10) return "png";
        if (bytes.length >= 12 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP")) return "webp";
        if (bytes.length >= 12 && ascii(bytes, 4, "ftyp")) {
            String brand = new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
            if (brand.equals("heic") || brand.equals("heix") || brand.equals("hevc") || brand.equals("hevx")
                    || brand.equals("heif") || brand.equals("mif1") || brand.equals("msf1")) return "heic";
        }
        return null;
    }

    private boolean ascii(byte[] bytes, int offset, String expected) {
        for (int i = 0; i < expected.length(); i++) if (bytes[offset + i] != (byte) expected.charAt(i)) return false;
        return true;
    }

    private ResponseStatusException invalid(String detail) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail); }

    public record ValidatedImage(byte[] bytes, String format) {}
}
