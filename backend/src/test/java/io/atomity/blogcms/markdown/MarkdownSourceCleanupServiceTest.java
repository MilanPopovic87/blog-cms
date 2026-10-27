
package io.atomity.blogcms.markdown;

import io.atomity.blogcms.IntegrationTest;
import io.atomity.blogcms.markdown.entity.MarkdownSource;
import io.atomity.blogcms.markdown.repository.MarkdownSourceRepository;
import io.atomity.blogcms.markdown.service.MarkdownSourceCleanupService;
import io.atomity.blogcms.media.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import io.atomity.blogcms.shared.ApiException;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownSourceCleanupServiceTest extends IntegrationTest {

    @Autowired
    private MarkdownSourceRepository repository;

    @Autowired
    private MarkdownSourceCleanupService cleanupService;

    @Autowired
    private StorageService storage;

    private MarkdownSource createSource(String filename, Instant createdAt) throws Exception {
        UUID id = UUID.randomUUID();
        String key = "test/" + id + ".md";
        byte[] content = "# Test".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        storage.store(key, content);

        MarkdownSource source = MarkdownSource.create(id, key, filename, content.length);

        Field createdAtField = MarkdownSource.class.getDeclaredField("createdAt");
        createdAtField.setAccessible(true);
        createdAtField.set(source, createdAt);

        return repository.saveAndFlush(source);
    }

    @Test
    void cleanupRemovesOldUnusedSources() throws Exception {
        MarkdownSource source = createSource(
            "old.md", Instant.now().minusSeconds(48 * 60 * 60L));

        cleanupService.cleanupOrphanedSources();

        assertThat(repository.findById(source.getId())).isEmpty();
        assertThatThrownBy(() -> storage.load(source.getStorageKey()))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void cleanupPreservesRecentUnusedSources() throws Exception {
        MarkdownSource source = createSource(
            "recent.md", Instant.now().minusSeconds(60 * 60L));

        cleanupService.cleanupOrphanedSources();

        assertThat(repository.findById(source.getId())).isPresent();
        assertThat(storage.load(source.getStorageKey()).exists()).isTrue();
    }
}
