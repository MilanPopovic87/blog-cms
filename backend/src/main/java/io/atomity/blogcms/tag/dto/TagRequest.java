package io.atomity.blogcms.tag.dto;

import io.atomity.blogcms.shared.Slugs;
import io.atomity.blogcms.tag.entity.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Create/rename body. An empty slug is derived from the name. */
public record TagRequest(
        @NotBlank(message = "is required")
        @Size(max = 60, message = "must be at most 60 characters")
        String name,

        @Size(max = Tag.MAX_SLUG_LENGTH, message = "must be at most " + Tag.MAX_SLUG_LENGTH + " characters")
        @Pattern(regexp = "^$|" + Slugs.PATTERN, message = Slugs.MESSAGE)
        String slug) {
}
