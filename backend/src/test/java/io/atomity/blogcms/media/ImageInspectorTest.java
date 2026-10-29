package io.atomity.blogcms.media;

import io.atomity.blogcms.media.service.ImageInspector;
import io.atomity.blogcms.shared.ApiException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageInspectorTest {

    @Test
    void readsPngJpegAndGif() {
        assertThat(ImageInspector.inspect(TestImages.encode("png", 120, 45)))
                .isEqualTo(new ImageInspector.ImageInfo("image/png", "png", 120, 45));
        assertThat(ImageInspector.inspect(TestImages.encode("jpg", 640, 480)))
                .isEqualTo(new ImageInspector.ImageInfo("image/jpeg", "jpg", 640, 480));
        assertThat(ImageInspector.inspect(TestImages.encode("gif", 33, 17)))
                .isEqualTo(new ImageInspector.ImageInfo("image/gif", "gif", 33, 17));
    }

    @Test
    void readsWebp() {
        assertThat(ImageInspector.inspect(TestImages.webpExtended(1200, 630)))
                .isEqualTo(new ImageInspector.ImageInfo("image/webp", "webp", 1200, 630));
    }

    @Test
    void identifiesByContentNotByName() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> ImageInspector.inspect(svg))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo("UNSUPPORTED_TYPE");
    }

    @Test
    void rejectsFilesThatOnlyStartLikeAGif() {
        byte[] notAGif = "GIF8-this is plain text, not an image".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> ImageInspector.inspect(notAGif))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo("UNSUPPORTED_TYPE");
    }

    @Test
    void acceptsBothGifVersions() {
        byte[] gif = TestImages.encode("gif", 33, 17);
        byte[] gif87 = gif.clone();
        gif87[4] = '7';
        assertThat(ImageInspector.inspect(gif87))
                .isEqualTo(new ImageInspector.ImageInfo("image/gif", "gif", 33, 17));
    }

    @Test
    void rejectsPngSignatureWithoutAnIhdrChunk() {
        byte[] png = TestImages.encode("png", 10, 10);
        byte[] broken = png.clone();
        broken[12] = 'X'; // IHDR → XHDR
        assertThatThrownBy(() -> ImageInspector.inspect(broken))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo("UNSUPPORTED_TYPE");
    }

    @Test
    void rejectsTruncatedHeaders() {
        byte[] png = TestImages.encode("png", 10, 10);
        assertThatThrownBy(() -> ImageInspector.inspect(java.util.Arrays.copyOf(png, 12)))
                .isInstanceOf(ApiException.class);
    }
}
