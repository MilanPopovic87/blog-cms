package io.atomity.blogcms.shared;

import io.atomity.blogcms.IntegrationTest;
import io.atomity.blogcms.author.repository.AuthorRepository;
import io.atomity.blogcms.post.repository.PostRepository;
import io.atomity.blogcms.tag.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The existsBySlug pre-checks are switched off, so a duplicate always reaches the unique
 * constraint: the same situation as losing a race, but deterministic. The message tells the
 * two paths apart (the pre-check names the slug, the constraint handler doesn't).
 */
class SlugConstraintApiTest extends IntegrationTest {

    @MockitoSpyBean
    private PostRepository postRepository;

    @MockitoSpyBean
    private TagRepository tagRepository;

    @MockitoSpyBean
    private AuthorRepository authorRepository;

    @BeforeEach
    void skipSlugPreChecks() {
        doReturn(false).when(postRepository).existsBySlug(anyString());
        doReturn(false).when(tagRepository).existsBySlug(anyString());
        doReturn(false).when(authorRepository).existsBySlug(anyString());
    }

    @Test
    void duplicatePostSlugHitsTheConstraint() throws Exception {
        assertSecondCreateIsSlugTaken("/api/v1/posts",
                Map.of("title", "Same", "slug", "same-slug", "content", Map.of("type", "doc")), "post");
    }

    @Test
    void duplicateTagSlugHitsTheConstraint() throws Exception {
        assertSecondCreateIsSlugTaken("/api/v1/tags", Map.of("name", "Same tag"), "tag");
    }

    @Test
    void duplicateAuthorSlugHitsTheConstraint() throws Exception {
        assertSecondCreateIsSlugTaken("/api/v1/authors", Map.of("name", "Same Author"), "author");
    }

    private void assertSecondCreateIsSlugTaken(String path, Map<String, Object> body, String resource) throws Exception {
        call(post(path), body).andExpect(status().isCreated());
        call(post(path), body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].errorCode").value("SLUG_TAKEN"))
                .andExpect(jsonPath("$.errors[0].message").value("Another " + resource + " already uses this slug"))
                .andExpect(jsonPath("$.errors[0].correlationId").isNotEmpty());
    }
}
