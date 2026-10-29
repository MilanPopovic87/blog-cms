package io.atomity.blogcms.author;

import com.fasterxml.jackson.databind.JsonNode;
import io.atomity.blogcms.IntegrationTest;
import io.atomity.blogcms.media.TestImages;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthorApiTest extends IntegrationTest {

    private JsonNode uploadImage() throws Exception {
        var file = new MockMultipartFile("file", "avatar.png", "image/png", TestImages.encode("png", 32, 32));
        return data(call(multipart("/api/v1/media").file(file)).andExpect(status().isCreated()));
    }

    @Test
    void createAuthorWithUnknownAvatarIdFailsWithUnknownReference() throws Exception {
        Map<String, Object> body = Map.of(
                "name", "Avatar repro",
                "avatarId", "00000000-0000-0000-0000-000000000000"
        );
        call(post("/api/v1/authors"), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].errorCode").value("UNKNOWN_REFERENCE"))
                .andExpect(jsonPath("$.errors[0].message").value("The avatar image does not exist"));
    }

    @Test
    void updateAuthorWithUnknownAvatarIdFailsWithUnknownReference() throws Exception {
        String authorId = data(call(post("/api/v1/authors"), Map.of("name", "Valid Author"))
                .andExpect(status().isCreated())).path("id").asText();

        Map<String, Object> body = Map.of(
                "name", "Avatar repro two",
                "avatarId", "00000000-0000-0000-0000-000000000000"
        );
        call(put("/api/v1/authors/" + authorId), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].errorCode").value("UNKNOWN_REFERENCE"))
                .andExpect(jsonPath("$.errors[0].message").value("The avatar image does not exist"));
    }

    @Test
    void updateUnknownAuthorReturnsNotFound() throws Exception {
        Map<String, Object> body = Map.of("name", "Avatar repro two");
        call(put("/api/v1/authors/00000000-0000-0000-0000-000000000099"), body)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].errorCode").value("NOT_FOUND"))
                .andExpect(jsonPath("$.errors[0].message").value("Author not found"));
    }

    @Test
    void createAndSetValidAvatar() throws Exception {
        String mediaId = uploadImage().path("id").asText();

        Map<String, Object> createBody = Map.of(
                "name", "Author With Avatar",
                "avatarId", mediaId
        );
        JsonNode created = data(call(post("/api/v1/authors"), createBody).andExpect(status().isCreated()));
        assertThat(created.path("avatar").path("id").asText()).isEqualTo(mediaId);

        String mediaId2 = uploadImage().path("id").asText();
        Map<String, Object> updateBody = Map.of(
                "name", "Author With Updated Avatar",
                "avatarId", mediaId2
        );
        JsonNode updated = data(call(put("/api/v1/authors/" + created.path("id").asText()), updateBody).andExpect(status().isOk()));
        assertThat(updated.path("avatar").path("id").asText()).isEqualTo(mediaId2);
    }

    @Test
    void updateWithNullAvatarClearsAvatar() throws Exception {
        String mediaId = uploadImage().path("id").asText();

        Map<String, Object> createBody = Map.of(
                "name", "Author To Clear Avatar",
                "avatarId", mediaId
        );
        JsonNode created = data(call(post("/api/v1/authors"), createBody).andExpect(status().isCreated()));
        assertThat(created.path("avatar").path("id").asText()).isEqualTo(mediaId);

        Map<String, Object> clearBody = new HashMap<>();
        clearBody.put("name", "Author With Cleared Avatar");
        clearBody.put("avatarId", null);

        JsonNode cleared = data(call(put("/api/v1/authors/" + created.path("id").asText()), clearBody).andExpect(status().isOk()));
        assertThat(cleared.path("avatar").isNull()).isTrue();
    }
}

