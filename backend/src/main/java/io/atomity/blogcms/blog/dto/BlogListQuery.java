package io.atomity.blogcms.blog.dto;

import io.atomity.blogcms.post.dto.PostListQuery;
import io.atomity.blogcms.post.entity.PostStatus;
import io.atomity.blogcms.shared.NulCharacters;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.Pageable;

/**
 * Query parameters of GET /api/v1/blogs. Only published posts are ever listed, and the
 * list defaults to newest first; sort, direction, size clamp and the id tiebreaker come
 * from the admin list so both APIs behave the same.
 */
public record BlogListQuery(
        @Pattern(regexp = NulCharacters.NONE, message = NulCharacters.MESSAGE) String q,
        @Pattern(regexp = NulCharacters.NONE, message = NulCharacters.MESSAGE) String tag,
        String sort,
        String direction,
        Integer page,
        Integer size) {

    private static final String DEFAULT_SORT = "publishedAt";

    public Pageable toPageable() {
        return new PostListQuery(PostStatus.PUBLISHED, null, null, null, null,
                sort == null ? DEFAULT_SORT : sort, direction, page, size).toPageable();
    }
}
