package io.atomity.blogcms.shared;

import com.fasterxml.jackson.databind.JsonNode;
import io.atomity.blogcms.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Raw HTTP bodies exercise Tomcat's multipart parser, which MockMvc does not run. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.servlet.multipart.max-file-size=1KB",
        "spring.servlet.multipart.max-request-size=4KB"
})
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MultipartRequestTest {

    private static final String BOUNDARY = "test-boundary";
    private static final String FILE_HEADER = "--" + BOUNDARY + "\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"cover.png\"\r\n"
            + "Content-Type: image/png\r\n\r\n";

    @Autowired
    private TestRestTemplate http;

    @Test
    void missingBoundaryOnPublicGetReturnsBadRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, "multipart/form-data");
        var response = http.exchange("/api/v1/blogs", HttpMethod.GET,
                new HttpEntity<>(headers), JsonNode.class);

        assertError(response, 400, "MALFORMED_REQUEST", "The upload could not be read");
    }

    @Test
    void truncatedUploadReturnsBadRequest() {
        var response = upload(FILE_HEADER + "partial");

        assertError(response, 400, "MALFORMED_REQUEST", "The upload could not be read");
    }

    @Test
    void fileAboveConfiguredLimitStillReturnsPayloadTooLarge() {
        // Above the file limit, below the request limit, with a complete closing boundary.
        var response = upload(FILE_HEADER + "x".repeat(1025) + "\r\n--" + BOUNDARY + "--\r\n");

        assertError(response, 413, "FILE_TOO_LARGE", "The file is larger than the upload limit");
    }

    private ResponseEntity<JsonNode> upload(String body) {
        var login = http.postForEntity("/api/v1/auth/login",
                Map.of("email", "admin@test.local", "password", "test-password"), JsonNode.class);
        assertThat(login.getStatusCode().value()).isEqualTo(200);
        assertThat(login.getBody()).isNotNull();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.getBody().at("/data/token").asText());
        headers.set(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + BOUNDARY);
        return http.exchange("/api/v1/media", HttpMethod.POST,
                new HttpEntity<>(body, headers), JsonNode.class);
    }

    private void assertError(ResponseEntity<JsonNode> response, int status, String code, String message) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("errors").size()).isEqualTo(1);
        assertThat(response.getBody().at("/errors/0/errorCode").asText()).isEqualTo(code);
        assertThat(response.getBody().at("/errors/0/message").asText()).isEqualTo(message);
        assertThat(response.getBody().at("/errors/0/correlationId").asText())
                .isNotBlank()
                .isEqualTo(response.getHeaders().getFirst("X-Correlation-ID"));
    }
}
