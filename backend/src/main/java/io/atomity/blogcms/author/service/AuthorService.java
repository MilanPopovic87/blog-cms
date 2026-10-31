package io.atomity.blogcms.author.service;

import io.atomity.blogcms.author.dto.AuthorRequest;
import io.atomity.blogcms.author.dto.AuthorResponse;
import io.atomity.blogcms.author.entity.Author;
import io.atomity.blogcms.author.repository.AuthorRepository;
import io.atomity.blogcms.media.entity.Media;
import io.atomity.blogcms.media.repository.MediaRepository;
import io.atomity.blogcms.shared.ApiException;
import io.atomity.blogcms.shared.Slugs;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthorService {

    private final AuthorRepository authorRepository;
    private final MediaRepository mediaRepository;

    public AuthorService(AuthorRepository authorRepository, MediaRepository mediaRepository) {
        this.authorRepository = authorRepository;
        this.mediaRepository = mediaRepository;
    }

    @Transactional(readOnly = true)
    public List<AuthorResponse> list() {
        Map<UUID, Long> counts = authorRepository.countPostsPerAuthor().stream()
                .collect(Collectors.toMap(AuthorRepository.PostCount::getAuthorId, AuthorRepository.PostCount::getPostCount));
        return authorRepository.findAllByOrderByNameAsc().stream()
                .map(author -> AuthorResponse.of(author, counts.getOrDefault(author.getId(), 0L)))
                .toList();
    }

    @Transactional
    public AuthorResponse create(AuthorRequest request) {
        String slug = resolveSlug(request, null);
        Author author = authorRepository.save(Author.create(request.name(), slug, request.bio(), avatar(request.avatarId())));
        return AuthorResponse.of(author, 0);
    }

    @Transactional
    public AuthorResponse update(UUID id, AuthorRequest request) {
        Author author = get(id);
        author.update(request.name(), resolveSlug(request, id), request.bio(), avatar(request.avatarId()));
        return AuthorResponse.of(author, authorRepository.countPosts(id));
    }

    /** The author is taken off every post that credits them; the posts themselves stay. */
    @Transactional
    public void delete(UUID id) {
        Author author = get(id);
        List<UUID> postIds = authorRepository.findPostIds(id);
        if (!postIds.isEmpty()) {
            authorRepository.removeFromAllPosts(id);
            authorRepository.renumberPositionsStepOne(postIds);
            authorRepository.renumberPositionsStepTwo(postIds);
        }
        authorRepository.delete(author);
    }

    @Transactional(readOnly = true)
    public Author get(UUID id) {
        return authorRepository.findById(id).orElseThrow(() -> ApiException.notFound("Author"));
    }

    private String resolveSlug(AuthorRequest request, UUID selfId) {
        String slug = request.slug() == null || request.slug().isBlank() ? Slugs.slugify(request.name()) : request.slug();
        if (slug.isEmpty()) {
            throw ApiException.badRequest("VALIDATION_ERROR", "slug could not be derived from the name; enter one");
        }
        boolean taken = selfId == null ? authorRepository.existsBySlug(slug) : authorRepository.existsBySlugAndIdNot(slug, selfId);
        if (taken) {
            throw ApiException.conflict("SLUG_TAKEN", "Another author already uses the slug \"" + slug + "\"");
        }
        return slug;
    }

    private Media avatar(UUID avatarId) {
        if (avatarId == null) {
            return null;
        }
        // MediaService.get() is not used because it throws 404 for URL-based media lookup,
        // whereas avatarId is a request body reference.
        return mediaRepository.findById(avatarId)
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_REFERENCE", "The avatar image does not exist"));
    }
}
