package io.atomity.blogcms.tag.service;

import io.atomity.blogcms.shared.ApiException;
import io.atomity.blogcms.shared.Slugs;
import io.atomity.blogcms.tag.dto.TagRequest;
import io.atomity.blogcms.tag.dto.TagResponse;
import io.atomity.blogcms.tag.entity.Tag;
import io.atomity.blogcms.tag.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TagService {

    private final TagRepository tagRepository;

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    @Transactional(readOnly = true)
    public List<TagResponse> list() {
        Map<UUID, Long> counts = tagRepository.countPostsPerTag().stream()
                .collect(Collectors.toMap(TagRepository.PostCount::getTagId, TagRepository.PostCount::getPostCount));
        return tagRepository.findAllByOrderByNameAsc().stream()
                .map(tag -> TagResponse.of(tag, counts.getOrDefault(tag.getId(), 0L)))
                .toList();
    }

    @Transactional
    public TagResponse create(TagRequest request) {
        Tag tag = tagRepository.save(Tag.create(request.name(), resolveSlug(request, null)));
        return TagResponse.of(tag, 0);
    }

    @Transactional
    public TagResponse update(UUID id, TagRequest request) {
        Tag tag = tagRepository.findById(id).orElseThrow(() -> ApiException.notFound("Tag"));
        tag.rename(request.name(), resolveSlug(request, id));
        return TagResponse.of(tag, tagRepository.countPosts(id));
    }

    /** post_tags rows go with it (ON DELETE CASCADE); the posts stay. */
    @Transactional
    public void delete(UUID id) {
        Tag tag = tagRepository.findById(id).orElseThrow(() -> ApiException.notFound("Tag"));
        tagRepository.delete(tag);
    }

    private String resolveSlug(TagRequest request, UUID selfId) {
        String slug = request.slug() == null || request.slug().isBlank()
                ? Slugs.slugify(request.name(), Tag.MAX_SLUG_LENGTH) : request.slug();
        if (slug.isEmpty()) {
            throw ApiException.badRequest("VALIDATION_ERROR", "slug could not be derived from the name; enter one");
        }
        boolean taken = selfId == null ? tagRepository.existsBySlug(slug) : tagRepository.existsBySlugAndIdNot(slug, selfId);
        if (taken) {
            throw ApiException.conflict("SLUG_TAKEN", "A tag with the slug \"" + slug + "\" already exists");
        }
        return slug;
    }
}
