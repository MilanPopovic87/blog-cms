package io.atomity.blogcms.media.dto;

import io.atomity.blogcms.shared.NulCharacters;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateMediaRequest(
        @NotNull(message = "is required")
        @Size(max = 300, message = "must be at most 300 characters")
        @Pattern(regexp = NulCharacters.NONE, message = NulCharacters.MESSAGE)
        String altText) {
}
