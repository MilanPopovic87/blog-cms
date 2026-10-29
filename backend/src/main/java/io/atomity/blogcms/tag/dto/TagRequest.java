package io.atomity.blogcms.tag.dto;

import io.atomity.blogcms.shared.NulCharacters;
import io.atomity.blogcms.shared.Slugs;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Create/rename body. An empty slug is derived from the name. */
public record TagRequest(
        @NotBlank(message = "is required")
        @Size(max = 60, message = "must be at most 60 characters")
        @Pattern(regexp = NulCharacters.NONE, message = NulCharacters.MESSAGE)
        String name,

        @Size(max = 80, message = "must be at most 80 characters")
        @Pattern(regexp = "^$|" + Slugs.PATTERN, message = Slugs.MESSAGE)
        String slug) {
}
