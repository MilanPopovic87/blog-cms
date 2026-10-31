package io.atomity.blogcms.media.controller;

import io.atomity.blogcms.media.entity.Media;
import io.atomity.blogcms.media.service.MediaService;
import io.atomity.blogcms.media.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MediaControllerTest {

    @Test
    void contentDispositionHeaderFormatting() {
        // ASCII filename
        assertThat(MediaController.contentDispositionHeader("photo.png"))
                .isEqualTo("inline; filename=\"photo.png\"");

        // Accented Latin filenames
        assertThat(MediaController.contentDispositionHeader("café.png"))
                .isEqualTo("inline; filename=\"cafe.png\"; filename*=UTF-8''caf%C3%A9.png");
        assertThat(MediaController.contentDispositionHeader("über.png"))
                .isEqualTo("inline; filename=\"uber.png\"; filename*=UTF-8''%C3%BCber.png");

        // CJK multi-byte filename
        assertThat(MediaController.contentDispositionHeader("封面.png"))
                .isEqualTo("inline; filename=\"image.png\"; filename*=UTF-8''%E5%B0%81%E9%9D%A2.png");

        // Quote-containing filename (escaped in fallback, percent-encoded in filename*)
        assertThat(MediaController.contentDispositionHeader("report\"final.png"))
                .isEqualTo("inline; filename=\"report\\\"final.png\"; filename*=UTF-8''report%22final.png");

        // Underscore-only ASCII filename (preserves original name instead of replacing with image.png)
        assertThat(MediaController.contentDispositionHeader("___.png"))
                .isEqualTo("inline; filename=\"___.png\"");

        // Null-safety
        assertThat(MediaController.contentDispositionHeader(null))
                .isEqualTo("inline; filename=\"image\"; filename*=UTF-8''");
    }

    @Test
    void fileEndpointReturnsStandardsCompliantInlineHeader() throws Exception {
        MediaService mediaService = mock(MediaService.class);
        StorageService storageService = mock(StorageService.class);
        MediaController controller = new MediaController(mediaService, storageService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        String[] testFilenames = {
                "photo.png",
                "café.png",
                "über.png",
                "封面.png",
                "report\"final.png"
        };

        for (String originalFilename : testFilenames) {
            UUID id = UUID.randomUUID();
            Media media = Media.create(id, "key-" + id, originalFilename, "image/png", 10, 100, 100);
            when(mediaService.get(id)).thenReturn(media);
            when(storageService.load("key-" + id)).thenReturn(new ByteArrayResource(new byte[]{1, 2, 3}));

            var result = mockMvc.perform(get("/api/v1/media/" + id + "/file"))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("Content-Disposition"))
                    .andReturn();

            String disposition = result.getResponse().getHeader("Content-Disposition");
            assertThat(disposition).isNotNull();
            assertThat(disposition).startsWith("inline;");

            // Spring's ContentDisposition parser must resolve the exact original filename
            ContentDisposition parsed = ContentDisposition.parse(disposition);
            assertThat(parsed.isInline()).isTrue();
            assertThat(parsed.getFilename()).isEqualTo(originalFilename);
        }
    }
}
