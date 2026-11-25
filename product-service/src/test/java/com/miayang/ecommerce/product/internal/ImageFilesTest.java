package com.miayang.ecommerce.product.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The real file type comes from the bytes, not from the name or the Content-Type header. */
class ImageFilesTest {

    @Test
    void recognisesJpegPngAndWebp() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0};
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

        assertThat(ImageFiles.detect(jpeg)).get().extracting(ImageFiles.ImageType::extension).isEqualTo("jpg");
        assertThat(ImageFiles.detect(png)).get().extracting(ImageFiles.ImageType::extension).isEqualTo("png");
        assertThat(ImageFiles.detect(webp)).get().extracting(ImageFiles.ImageType::extension).isEqualTo("webp");
    }

    @Test
    void rejectsEverythingElse() {
        assertThat(ImageFiles.detect("GIF89a".getBytes())).isEmpty();
        assertThat(ImageFiles.detect("MZ program".getBytes())).isEmpty();
        assertThat(ImageFiles.detect(new byte[0])).isEmpty();
        assertThat(ImageFiles.detect(new byte[] {(byte) 0xFF})).isEmpty();
    }
}
