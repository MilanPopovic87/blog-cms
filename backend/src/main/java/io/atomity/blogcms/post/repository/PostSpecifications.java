package io.atomity.blogcms.post.repository;

import io.atomity.blogcms.post.entity.Post;
import io.atomity.blogcms.post.entity.PostStatus;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

/** Filters for the posts list. Each returns null when unused, which Specification.where ignores. */
public final class PostSpecifications {

    private PostSpecifications() {
    }

    public static Specification<Post> hasStatus(PostStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Post> hasFeatured(Boolean featured) {
        return featured == null ? null : (root, query, cb) -> cb.equal(root.get("featured"), featured);
    }

    public static Specification<Post> hasTag(UUID tagId) {
        return tagId == null ? null : (root, query, cb) -> cb.equal(root.join("tags").get("id"), tagId);
    }

    public static Specification<Post> hasTagSlug(String tagSlug) {
        if (tagSlug == null || tagSlug.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.join("tags").get("slug"), tagSlug.strip());
    }

    public static Specification<Post> hasAuthor(UUID authorId) {
        return authorId == null ? null : (root, query, cb) -> cb.equal(root.join("authors").get("id"), authorId);
    }

    /**
     * Case-insensitive substring match on title, excerpt, slug and body text. Uses ILIKE,
     * which the pg_trgm indexes from V1 can serve.
     */
    public static Specification<Post> matches(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(q.strip()) + "%";
        return (root, query, cb) -> {
            var hcb = (HibernateCriteriaBuilder) cb;
            return cb.or(
                    hcb.ilike(root.get("title"), pattern, '\\'),
                    hcb.ilike(root.get("excerpt"), pattern, '\\'),
                    hcb.ilike(root.get("slug"), pattern, '\\'),
                    hcb.ilike(root.get("contentText"), pattern, '\\'));
        };
    }

    /** The user's text is matched literally: % and _ are not wildcards. */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
