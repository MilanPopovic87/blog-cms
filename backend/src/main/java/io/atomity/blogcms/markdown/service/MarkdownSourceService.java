package io.atomity.blogcms.markdown.service;

import io.atomity.blogcms.markdown.dto.MarkdownSourceRef;
import io.atomity.blogcms.markdown.entity.MarkdownSource;
import io.atomity.blogcms.markdown.repository.MarkdownSourceRepository;
import io.atomity.blogcms.media.storage.StorageService;
import io.atomity.blogcms.shared.ApiException;
import io.atomity.blogcms.shared.Filenames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
public class MarkdownSourceService {

    private static final Logger log = LoggerFactory.getLogger(MarkdownSourceService.class);

    /** Same limit as the editor's import. */
    static final long MAX_BYTES = 1024 * 1024;

    private final MarkdownSourceRepository repository;
    private final StorageService storage;
    private final TransactionTemplate transactionTemplate;

    public MarkdownSourceService(
            MarkdownSourceRepository repository,
            StorageService storage,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.storage = storage;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public MarkdownSource get(UUID id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Markdown file"));
    }

    /**
     * Deletes the row and, once that commits, the stored file, as long as no post still
     * uses it (a duplicated post shares its source). Call after the post change is flushed.
     */
    @Transactional
    public void deleteIfUnused(MarkdownSource source) {
        if (source == null || repository.countUsages(source.getId()) > 0) {
            return;
        }
        repository.delete(source);
        String key = source.getStorageKey();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.delete(key);
            }
        });
    }
    @Transactional
    public void deleteUnusedById(UUID id) {
        MarkdownSource source = repository.findById(id).orElse(null);
        if (source != null) {
            deleteIfUnused(source);
        }
    }


    /**
     * Deletes the row and its stored file, refusing while a post still uses it.
     * Mirrors {@code MediaService.delete}: the row is loaded and deleted in one
     * short transaction, then the (best-effort) storage delete runs after it commits.
     */
    public void delete(UUID id) {
        String key = transactionTemplate.execute(status -> {
            MarkdownSource source = repository.findById(id)
                    .orElseThrow(() -> ApiException.notFound("Markdown file"));
            if (repository.countUsages(id) > 0) {
                throw ApiException.conflict("MARKDOWN_SOURCE_IN_USE",
                        "This Markdown file is used by a post. Remove it there first.");
            }
            String storageKey = source.getStorageKey();
            repository.delete(source);
            return storageKey;
        });
        if (key != null) {
            storage.delete(key);
        }
    }

    /**
     * Stores the file, then records it. WebDAV runs outside a database
     * transaction so a slow Nextcloud call cannot hold a pool connection.
     * If the database write fails the file is removed again.
     */
    public MarkdownSourceRef upload(MultipartFile file) {
        String name = Filenames.clean(file.getOriginalFilename(), "post.md");
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".md") && !lower.endsWith(".markdown")) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_TYPE", "Only .md and .markdown files are supported");
        }
        if (file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "The file is empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Markdown files must be 1 MB or smaller");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("READ_FAILED", "Could not read the uploaded file");
        }
        if (!isText(bytes)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_TYPE", "The file is not a readable text file");
        }

        UUID id = UUID.randomUUID();
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String key = "markdown/%d/%02d/%s.md".formatted(now.getYear(), now.getMonthValue(), id);

        storage.store(key, bytes);
        try {
            MarkdownSourceRef recorded = transactionTemplate.execute(status -> {
                MarkdownSource source = repository.save(MarkdownSource.create(id, key, name, bytes.length));
                log.info("Stored markdown file {} ({} bytes)", source.getId(), bytes.length);
                return MarkdownSourceRef.of(source);
            });
            if (recorded == null) {
                throw new IllegalStateException("Markdown record was not saved");
            }
            return recorded;
        } catch (RuntimeException | Error ex) {
            try {
                storage.delete(key);
            } catch (RuntimeException | Error cleanupException) {
                log.warn("Failed to delete stored file {} after a database error", key, cleanupException);
                ex.addSuppressed(cleanupException);
            }
            throw ex;
        }
    }

    /** Valid UTF-8 without NUL characters; anything else is a binary file with a .md name. */
    static boolean isText(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return text.indexOf('\0') < 0;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
