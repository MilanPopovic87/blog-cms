package io.atomity.blogcms.tag;

import com.fasterxml.jackson.databind.JsonNode;
import io.atomity.blogcms.IntegrationTest;
import io.atomity.blogcms.shared.Slugs;
import io.atomity.blogcms.tag.entity.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TagApiTest extends IntegrationTest {

    @Test
    void derivedSlugIsCutToTheColumnLength() throws Exception {
        // 60 characters, the most TagRequest allows. NFKD turns each "½" into "1⁄2",
        // so the uncapped slug would be far longer than tags.slug (VARCHAR(80)).
        String name = "x " + "½".repeat(58);
        assertThat(name).hasSize(60);

        JsonNode tag = data(call(post("/api/v1/tags"), Map.of("name", name, "slug", ""))
                .andExpect(status().isCreated()));

        assertThat(tag.path("slug").asText())
                .hasSizeLessThanOrEqualTo(Tag.MAX_SLUG_LENGTH)
                .matches(Slugs.PATTERN);
    }
}
