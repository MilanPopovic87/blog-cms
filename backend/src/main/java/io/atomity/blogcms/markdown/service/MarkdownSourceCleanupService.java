
package io.atomity.blogcms.markdown.service;

import io.atomity.blogcms.markdown.repository.MarkdownSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MarkdownSourceCleanupService {

    private static final Logger log =
        LoggerFactory.getLogger(MarkdownSourceCleanupService.class);

    private static final Duration ORPHAN_AGE_THRESHOLD = Duration.ofHours(24);

    private final MarkdownSourceRepository repository;
    private final MarkdownSourceService markdownSourceService;

    public MarkdownSourceCleanupService(
        MarkdownSourceRepository repository,
        MarkdownSourceService markdownSourceService) {
        this.repository = repository;
        this.markdownSourceService = markdownSourceService;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void cleanupOrphanedSources() {
        Instant cutoff = Instant.now().minus(ORPHAN_AGE_THRESHOLD);

        List<UUID> candidateIds = repository.findUnusedCreatedBefore(cutoff)
            .stream()
            .map(source -> source.getId())
            .toList();

        for (UUID id : candidateIds) {
            try {
                markdownSourceService.deleteUnusedById(id);
            } catch (RuntimeException exception) {
                log.warn("Failed to clean up orphaned Markdown source {}", id, exception);
            }
        }

        if (!candidateIds.isEmpty()) {
            log.info("Checked {} potentially orphaned Markdown sources", candidateIds.size());
        }
    }
}
