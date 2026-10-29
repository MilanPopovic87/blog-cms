package io.atomity.blogcms.author.dto;

import io.atomity.blogcms.shared.NulCharacters;
import io.atomity.blogcms.shared.Slugs;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Create/update body. An empty slug is derived from the name. */
public record AuthorRequest(
        @NotBlank(message = "is required")
        @Size(max = 100, message = "must be at most 100 characters")
        @Pattern(regexp = NulCharacters.NONE, message = NulCharacters.MESSAGE)
        String name,

        @Size(max = 120, message = "must be at most 120 characters")
        @Pattern(regexp = "^$|" + Slugs.PATTERN, message = Slugs.MESSAGE)
        String slug,

        @Size(max = 500, message = "must be at most 500 characters")
        @Pattern(regexp = NulCharacters.NONE, message = NulCharacters.MESSAGE)
        String bio,

        UUID avatarId) {
}
