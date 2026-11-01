package io.atomity.blogcms.post;

import com.fasterxml.jackson.databind.JsonNode;
import io.atomity.blogcms.IntegrationTest;
import org.junit.jupiter.api.Test;
import io.atomity.blogcms.media.TestImages;
import org.springframework.mock.web.MockMultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

class PostApiTest extends IntegrationTest {

    // ── Builders ───────────────────────────────────────────────────────────

    static Map<String, Object> doc(String... paragraphs) {
        List<Map<String, Object>> content = new java.util.ArrayList<>();
        for (String text : paragraphs) {
            content.add(Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", text))));
        }
        return Map.of("type", "doc", "content", content);
    }

    static Map<String, Object> postBody(String title, Map<String, Object> content) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("content", content);
        return body;
    }

    private JsonNode createPost(String title, String... paragraphs) throws Exception {
        return data(call(post("/api/v1/posts"), postBody(title, doc(paragraphs))).andExpect(status().isCreated()));
    }

    private String createAuthor(String name) throws Exception {
        return data(call(post("/api/v1/authors"), Map.of("name", name)).andExpect(status().isCreated())).path("id").asText();
    }

    private String createTag(String name) throws Exception {
        return data(call(post("/api/v1/tags"), Map.of("name", name)).andExpect(status().isCreated())).path("id").asText();
    }

    private JsonNode uploadImage() throws Exception {
        var file = new MockMultipartFile("file", "photo.png", "image/png", TestImages.encode("png", 20, 20));
        return data(call(multipart("/api/v1/media").file(file)).andExpect(status().isCreated()));
    }

    static Map<String, Object> imageDoc(JsonNode media) {
        Map<String, Object> image = Map.of("type", "image",
                "attrs", Map.of("src", media.path("url").asText(), "mediaId", media.path("id").asText()));
        return Map.of("type", "doc", "content", List.of(image));
    }

    // ── Create & read ──────────────────────────────────────────────────────

    @Test
    void createStartsAsDraftWithDerivedSlugAndCounts() throws Exception {
        JsonNode created = createPost("Hello, Wörld!", "One two three four.", "Five six.");

        assertThat(created.path("status").asText()).isEqualTo("DRAFT");
        assertThat(created.path("slug").asText()).isEqualTo("hello-world");
        assertThat(created.path("wordCount").asInt()).isEqualTo(6);
        assertThat(created.path("readingTimeMinutes").asInt()).isEqualTo(1);
        assertThat(created.path("publishedAt").isNull()).isTrue();
        assertThat(created.path("content").path("type").asText()).isEqualTo("doc");

        call(get("/api/v1/posts/" + created.path("id").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Hello, Wörld!"))
                .andExpect(jsonPath("$.data.content.content[0].content[0].text").value("One two three four."));
    }

    @Test
    void untitledDraftGetsAPlaceholderSlug() throws Exception {
        JsonNode created = createPost("");
        assertThat(created.path("slug").asText()).startsWith("draft-");
    }

    @Test
    void duplicateSlugIsRejected() throws Exception {
        createPost("Same title", "text");
        call(post("/api/v1/posts"), postBody("Same title", doc("other")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].errorCode").value("SLUG_TAKEN"));
    }

    @Test
    void invalidFieldsAreRejected() throws Exception {
        Map<String, Object> body = postBody("x".repeat(201), doc("text"));
        body.put("slug", "Not A Slug");
        call(post("/api/v1/posts"), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].errorCode").value("VALIDATION_ERROR"));

        call(post("/api/v1/posts"), postBody("Bad body", Map.of("type", "paragraph")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].errorCode").value("INVALID_CONTENT"));
    }

    @Test
    void unsafeLinksAreRejected() throws Exception {
        Map<String, Object> link = Map.of("type", "link", "attrs", Map.of("href", "javascript:alert(1)"));
        Map<String, Object> content = Map.of("type", "doc", "content", List.of(Map.of("type", "paragraph",
                "content", List.of(Map.of("type", "text", "text", "click", "marks", List.of(link))))));
        call(post("/api/v1/posts"), postBody("XSS", content))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].errorCode").value("UNSAFE_LINK"));
    }

    @Test
    void nulCharactersAreRejectedBeforeTheyReachTheDatabase() throws Exception {
        // Postgres can't store U+0000 in text or jsonb; it must be a 400, not a 500 from the database.
        call(post("/api/v1/posts"), postBody("Hello\u0000world", doc("Some words")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].message").value("title must not contain NUL characters"));

        Map<String, Object> seo = postBody("Fine title", doc("Some words"));
        seo.put("canonicalUrl", "https://example.com/\u0000");
        call(post("/api/v1/posts"), seo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("canonicalUrl must not contain NUL characters"));

        call(post("/api/v1/posts"), postBody("Fine title", doc("Hello\u0000world")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].errorCode").value("INVALID_CONTENT"))
                .andExpect(jsonPath("$.errors[0].message").value("The post body must not contain NUL characters"));

        call(get("/api/v1/posts/counts")).andExpect(jsonPath("$.data.ALL").value(0));
    }

    @Test
    void authorsKeepTheirOrderAndUnknownIdsFail() throws Exception {
        String lena = createAuthor("Lena");
        String rahul = createAuthor("Rahul");
        String tag = createTag("FinOps");

        Map<String, Object> body = postBody("With people", doc("text"));
        body.put("authorIds", List.of(rahul, lena));
        body.put("tagIds", List.of(tag));
        JsonNode created = data(call(post("/api/v1/posts"), body).andExpect(status().isCreated()));
        assertThat(created.path("authors").findValuesAsText("name")).containsExactly("Rahul", "Lena");
        assertThat(created.path("tags").get(0).path("slug").asText()).isEqualTo("finops");

        body.put("title", "Ghost author");
        body.put("authorIds", List.of("00000000-0000-0000-0000-000000000000"));
        call(post("/api/v1/posts"), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].errorCode").value("UNKNOWN_REFERENCE"));
    }

    // ── Update & optimistic locking ────────────────────────────────────────

    @Test
    void updateRequiresCurrentVersion() throws Exception {
        JsonNode created = createPost("Versioned", "first");
        String id = created.path("id").asText();
        long version = created.path("version").asLong();

        Map<String, Object> edit = postBody("Versioned v2", doc("second"));
        edit.put("slug", "versioned");
        edit.put("version", version);
        JsonNode updated = data(call(put("/api/v1/posts/" + id), edit).andExpect(status().isOk()));
        assertThat(updated.path("title").asText()).isEqualTo("Versioned v2");
        assertThat(updated.path("version").asLong()).isGreaterThan(version);

        // A second save from an editor still holding the old version is refused.
        call(put("/api/v1/posts/" + id), edit)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].errorCode").value("VERSION_CONFLICT"));

        edit.remove("version");
        call(put("/api/v1/posts/" + id), edit).andExpect(status().isBadRequest());
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Test
    void publishRequiresTitleAndContent() throws Exception {
        String empty = createPost("").path("id").asText();
        call(post("/api/v1/posts/" + empty + "/publish"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].errorCode").value("NOT_PUBLISHABLE"))
                .andExpect(jsonPath("$.errors[0].message").value("Add a title and some content before publishing."));
    }

    @Test
    void publishedPostCannotBeSavedWithoutTitle() throws Exception {
        // Arrange
        String id = createPost("Live post", "Some words here").path("id").asText();
        JsonNode published = data(call(post("/api/v1/posts/" + id + "/publish")).andExpect(status().isOk()));

        // Act: PUT the same content and slug, but title "", with the version from the publish response
        // Assert 1
        Map<String, Object> edit = postBody("", doc("Some words here"));
        edit.put("slug", published.path("slug").asText());
        edit.put("version", published.path("version").asLong());
        call(put("/api/v1/posts/" + id), edit)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].errorCode").value("NOT_PUBLISHABLE"))
                .andExpect(jsonPath("$.errors[0].message")
                .value("A published post can't be saved without a title. Unpublish it first."));

        // Assert 2
        JsonNode reloaded = data(call(get("/api/v1/posts/" + id)).andExpect(status().isOk()));
        assertThat(reloaded.path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(reloaded.path("title").asText()).isEqualTo("Live post");
        assertThat(reloaded.path("version").asLong()).isEqualTo(published.path("version").asLong());
    }

    @Test
    void publishedPostCannotBeSavedWithoutContent() throws Exception {
        // Arrange
        String id = createPost("Live post", "Some words here").path("id").asText();
        JsonNode published = data(call(post("/api/v1/posts/" + id + "/publish")).andExpect(status().isOk()));

        // Act: PUT the same title and slug, but an empty body, with the version from the publish response
        // Assert 1
        Map<String, Object> edit = postBody("Live post", doc());
        edit.put("slug", published.path("slug").asText());
        edit.put("version", published.path("version").asLong());
        call(put("/api/v1/posts/" + id), edit)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].errorCode").value("NOT_PUBLISHABLE"))
                .andExpect(jsonPath("$.errors[0].message")
                .value("A published post can't be saved without some content. Unpublish it first."));

        // Assert 2
        JsonNode reloaded = data(call(get("/api/v1/posts/" + id)).andExpect(status().isOk()));
        assertThat(reloaded.path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(reloaded.path("title").asText()).isEqualTo("Live post");
        assertThat(reloaded.path("version").asLong()).isEqualTo(published.path("version").asLong());
        assertThat(reloaded.path("wordCount").asInt()).isEqualTo(3);
    }

    @Test
    void publishedPostCanStillBeEdited() throws Exception {
        // Arrange: create "Live post" with some text, publish it, keep the publish response
        String id = createPost("Live post", "Some words here").path("id").asText();
        JsonNode published = data(call(post("/api/v1/posts/" + id + "/publish")).andExpect(status().isOk()));

        // Act: a normal edit, with a new title and new text, same slug, current version
        Map<String, Object> edit = postBody("Live post, updated", doc("Some new words here"));
        edit.put("slug", published.path("slug").asText());
        edit.put("version", published.path("version").asLong());
        JsonNode updated = data(call(put("/api/v1/posts/" + id), edit).andExpect(status().isOk()));

        // Assert: the edit is saved and the post stays live
        assertThat(updated.path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(updated.path("title").asText()).isEqualTo("Live post, updated");
        assertThat(updated.path("wordCount").asInt()).isEqualTo(4);
        assertThat(updated.path("version").asLong()).isGreaterThan(published.path("version").asLong());
    }

    @Test
    void draftCanStillBeSavedEmpty() throws Exception {
        // Arrange: a draft with some text (not published)
        JsonNode created = createPost("Draft post", "Some words here");

        // Act: save it with an empty title and an empty body, same slug, current version
        Map<String, Object> edit = postBody("", doc());
        edit.put("slug", created.path("slug").asText());
        edit.put("version", created.path("version").asLong());
        JsonNode saved = data(call(put("/api/v1/posts/" + created.path("id").asText()), edit)
                .andExpect(status().isOk()));

        // Assert: drafts aren't live, so the empty save is accepted and the post stays a draft
        assertThat(saved.path("status").asText()).isEqualTo("DRAFT");
        assertThat(saved.path("title").asText()).isEmpty();
        assertThat(saved.path("wordCount").asInt()).isZero();
    }

    @Test
    void publishedImageOnlyPostCanStillBeSaved() throws Exception {
        // Arrange: a post whose only content is a library image, published
        JsonNode media = uploadImage();
        String id = data(call(post("/api/v1/posts"), postBody("Photo post", imageDoc(media)))
                .andExpect(status().isCreated())).path("id").asText();
        JsonNode published = data(call(post("/api/v1/posts/" + id + "/publish")).andExpect(status().isOk()));

        // Act: rename it, keeping the image as the only content
        Map<String, Object> edit = postBody("Photo post, renamed", imageDoc(media));
        edit.put("slug", published.path("slug").asText());
        edit.put("version", published.path("version").asLong());
        JsonNode updated = data(call(put("/api/v1/posts/" + id), edit).andExpect(status().isOk()));

        // Assert: an image counts as content, so the save is accepted and the post stays live
        assertThat(updated.path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(updated.path("title").asText()).isEqualTo("Photo post, renamed");
        assertThat(updated.path("wordCount").asInt()).isZero();
        assertThat(updated.path("version").asLong()).isGreaterThan(published.path("version").asLong());
    }

    @Test
    void publishKeepsFirstPublicationDate() throws Exception {
        String id = createPost("Lifecycle", "Some words here.").path("id").asText();

        JsonNode published = data(call(post("/api/v1/posts/" + id + "/publish")).andExpect(status().isOk()));
        assertThat(published.path("status").asText()).isEqualTo("PUBLISHED");
        String firstPublishedAt = published.path("publishedAt").asText();
        assertThat(firstPublishedAt).isNotBlank();

        assertThat(data(call(post("/api/v1/posts/" + id + "/unpublish"))).path("status").asText()).isEqualTo("DRAFT");
        assertThat(data(call(post("/api/v1/posts/" + id + "/archive"))).path("status").asText()).isEqualTo("ARCHIVED");
        JsonNode republished = data(call(post("/api/v1/posts/" + id + "/publish")));
        assertThat(republished.path("publishedAt").asText()).isEqualTo(firstPublishedAt);
    }

    @Test
    void duplicateCreatesUnpublishedCopyWithFreeSlug() throws Exception {
        String id = createPost("Original", "Body text.").path("id").asText();
        call(post("/api/v1/posts/" + id + "/publish")).andExpect(status().isOk());

        JsonNode first = data(call(post("/api/v1/posts/" + id + "/duplicate")).andExpect(status().isCreated()));
        JsonNode second = data(call(post("/api/v1/posts/" + id + "/duplicate")).andExpect(status().isCreated()));
        assertThat(first.path("slug").asText()).isEqualTo("original-copy");
        assertThat(second.path("slug").asText()).isEqualTo("original-copy-2");
        assertThat(first.path("status").asText()).isEqualTo("DRAFT");
        assertThat(first.path("title").asText()).isEqualTo("Original (copy)");
        assertThat(first.path("wordCount").asInt()).isEqualTo(2);
    }

    @Test
    void deleteRemovesPost() throws Exception {
        String id = createPost("Doomed", "text").path("id").asText();
        call(delete("/api/v1/posts/" + id)).andExpect(status().isNoContent());
        call(get("/api/v1/posts/" + id)).andExpect(status().isNotFound());
    }

    // ── Listing ────────────────────────────────────────────────────────────

    @Test
    void listFiltersSearchesSortsAndPaginates() throws Exception {
        String tag = createTag("Kubernetes");
        Map<String, Object> tagged = postBody("Rightsizing pods", doc("Kubernetes requests and limits."));
        tagged.put("tagIds", List.of(tag));
        call(post("/api/v1/posts"), tagged).andExpect(status().isCreated());
        createPost("Carbon footprint", "Grid mix matters.");
        String published = createPost("Spot instances", "Interruptions happen.").path("id").asText();
        call(post("/api/v1/posts/" + published + "/publish")).andExpect(status().isOk());

        // Create one featured post and one regular (non-featured) post.
        Map<String, Object> featuredBody = postBody("Featured guide", doc("A featured article."));
        featuredBody.put("featured", true);
        call(post("/api/v1/posts"), featuredBody).andExpect(status().isCreated());

        Map<String, Object> regularBody = postBody("Regular guide", doc("A regular article."));
        regularBody.put("featured", false);
        call(post("/api/v1/posts"), regularBody).andExpect(status().isCreated());

        // Substring search covers the body text, case-insensitively.
        call(get("/api/v1/posts?q=GRID")).andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].title").value("Carbon footprint"));
        // % is matched literally, not as a wildcard.
        call(get("/api/v1/posts?q=%25")).andExpect(jsonPath("$.data.length()").value(0));

        call(get("/api/v1/posts?status=PUBLISHED")).andExpect(jsonPath("$.data.length()").value(1));
        call(get("/api/v1/posts?tag=" + tag)).andExpect(jsonPath("$.data[0].title").value("Rightsizing pods"));

        // Featured filter distinguishes true, false, and an omitted parameter.
        call(get("/api/v1/posts?featured=true"))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].title").value("Featured guide"))
            .andExpect(jsonPath("$.data[0].featured").value(true));

        call(get("/api/v1/posts?featured=false"))
            .andExpect(jsonPath("$.data.length()").value(4))
            .andExpect(jsonPath("$.data[*].featured").value(
                org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(false))));

        call(get("/api/v1/posts"))
            .andExpect(jsonPath("$.meta.totalElements").value(5));

        // The featured filter should combine with the text search filter.
        call(get("/api/v1/posts?featured=true&q=featured"))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].title").value("Featured guide"));

        call(get("/api/v1/posts?sort=title&direction=asc&size=2"))
            .andExpect(jsonPath("$.data[0].title").value("Carbon footprint"))
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.meta.totalElements").value(5))
            .andExpect(jsonPath("$.meta.totalPages").value(3));
        call(get("/api/v1/posts?sort=title&direction=asc&size=2&page=1"))
            .andExpect(jsonPath("$.data[0].title").value("Regular guide"));

        call(get("/api/v1/posts?sort=password")).andExpect(status().isBadRequest());
        call(get("/api/v1/posts?status=BOGUS")).andExpect(status().isBadRequest());
    }

    @Test
    void featuredFilterCombinesWithStatusTagAndAuthor() throws Exception {
        String author = createAuthor("Shared author");
        String tag = createTag("Shared tag");

        Map<String, Object> featuredBody = postBody("Featured guide", doc("Featured content."));
        featuredBody.put("featured", true);
        featuredBody.put("authorIds", List.of(author));
        featuredBody.put("tagIds", List.of(tag));
        call(post("/api/v1/posts"), featuredBody).andExpect(status().isCreated());

        Map<String, Object> regularBody = postBody("Regular guide", doc("Regular content."));
        regularBody.put("featured", false);
        regularBody.put("authorIds", List.of(author));
        regularBody.put("tagIds", List.of(tag));
        call(post("/api/v1/posts"), regularBody).andExpect(status().isCreated());

        // Both posts are drafts, so featured must further narrow the status results.
        call(get("/api/v1/posts?status=DRAFT&featured=true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].title").value("Featured guide"));

        // Both posts have the same tag, so featured must further narrow the tag results.
        call(get("/api/v1/posts?tag=" + tag + "&featured=true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].title").value("Featured guide"));

        // Both posts have the same author, so featured must further narrow the author results.
        call(get("/api/v1/posts?author=" + author + "&featured=true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].title").value("Featured guide"));
    }

    @Test
    void searchRejectsNulCharacters() throws Exception {
        call(get("/api/v1/posts").param("q", "Grid\u0000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].message").value("q must not contain NUL characters"));
    }

    @Test
    void listRejectsPageWhenOffsetExceedsIntegerMaxValue() throws Exception {
        call(get("/api/v1/posts?page=2147483647"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].errorCode").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.errors[0].message").value("page is out of range"));
    }

    @Test
    void countsCoverEveryStatus() throws Exception {
        createPost("Draft one", "x");
        String id = createPost("Published one", "y").path("id").asText();
        call(post("/api/v1/posts/" + id + "/publish"));

        call(get("/api/v1/posts/counts"))
                .andExpect(jsonPath("$.data.ALL").value(2))
                .andExpect(jsonPath("$.data.DRAFT").value(1))
                .andExpect(jsonPath("$.data.PUBLISHED").value(1))
                .andExpect(jsonPath("$.data.ARCHIVED").value(0));
    }

    // ── Authors & tags interplay ───────────────────────────────────────────

    @Test
    void deletingAnAuthorKeepsPostsAndCloseUpTheByline() throws Exception {
        String a = createAuthor("Alice");
        String b = createAuthor("Bob");
        String c = createAuthor("Carol");
        Map<String, Object> body = postBody("Three authors", doc("text"));
        body.put("authorIds", List.of(a, b, c));
        String id = data(call(post("/api/v1/posts"), body)).path("id").asText();

        call(delete("/api/v1/authors/" + b)).andExpect(status().isNoContent());

        JsonNode post = data(call(get("/api/v1/posts/" + id)).andExpect(status().isOk()));
        assertThat(post.path("authors").findValuesAsText("name")).containsExactly("Alice", "Carol");
        call(get("/api/v1/authors")).andExpect(jsonPath("$.data[0].postCount").value(1));
    }

    @Test
    void deletingATagRemovesItFromPosts() throws Exception {
        String tag = createTag("Temporary");
        Map<String, Object> body = postBody("Tagged", doc("text"));
        body.put("tagIds", List.of(tag));
        String id = data(call(post("/api/v1/posts"), body)).path("id").asText();

        call(delete("/api/v1/tags/" + tag)).andExpect(status().isNoContent());
        call(get("/api/v1/posts/" + id)).andExpect(jsonPath("$.data.tags.length()").value(0));
    }

    @Test
    void tagAndAuthorTextRejectNulCharacters() throws Exception {
        call(post("/api/v1/tags"), Map.of("name", "Dev\u0000Ops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].message").value("name must not contain NUL characters"));

        call(post("/api/v1/authors"), Map.of("name", "Lena", "bio", "Writes\u0000about FinOps"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].message").value("bio must not contain NUL characters"));

        call(get("/api/v1/tags")).andExpect(jsonPath("$.data.length()").value(0));
        call(get("/api/v1/authors")).andExpect(jsonPath("$.data.length()").value(0));
    }
}
