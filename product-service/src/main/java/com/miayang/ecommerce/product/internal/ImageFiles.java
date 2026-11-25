package com.miayang.ecommerce.product.internal;

import java.util.Optional;

/**
 * Works out the real image type from the file's first bytes ("magic numbers").
 * We don't trust the file name or the Content-Type the client sends: anyone can rename
 * malware.exe to cat.png. Only real JPEG, PNG and WebP files are accepted.
 */
final class ImageFiles {

    static final int MAX_BYTES = 2 * 1024 * 1024;   // 2 MB

    record ImageType(String contentType, String extension) {
    }

    private ImageFiles() {
    }

    static Optional<ImageType> detect(byte[] bytes) {
        if (startsWith(bytes, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(new ImageType("image/jpeg", "jpg"));
        }
        if (startsWith(bytes, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(new ImageType("image/png", "png"));
        }
        if (startsWith(bytes, 0, 'R', 'I', 'F', 'F') && startsWith(bytes, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of(new ImageType("image/webp", "webp"));
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] bytes, int offset, int... expected) {
        if (bytes.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((bytes[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
