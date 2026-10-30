package io.atomity.blogcms.media;

import com.fasterxml.jackson.databind.JsonNode;
import io.atomity.blogcms.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ContentDisposition;
import org.springframework.mock.web.MockMultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration test verifying that embedded Tomcat serializes standards-compliant
 * HTTP wire response headers for GET /api/v1/media/{id}/file in the full Spring Boot application.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MediaTomcatIntegrationTest extends IntegrationTest {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    private record TestCase(String filename, String expectedHeader) {}

    private JsonNode upload(String filename, String declaredType, byte[] bytes) throws Exception {
        var file = new MockMultipartFile("file", filename, declaredType, bytes);
        return data(call(multipart("/api/v1/media").file(file)).andExpect(status().isCreated()));
    }

    @Test
    @DisplayName("Upload requires authentication (401), while media file retrieval is public (200)")
    void exercisesAuthenticationAndAuthorizationRequirements() throws Exception {
        byte[] png = TestImages.encode("png", 10, 10);
        var unauthenticatedFile = new MockMultipartFile("file", "test.png", "image/png", png);

        // 1. Upload without authentication is rejected (401 Unauthorized)
        mockMvc.perform(multipart("/api/v1/media").file(unauthenticatedFile))
                .andExpect(status().isUnauthorized());

        // 2. Upload with authentication succeeds (201 Created)
        JsonNode media = upload("auth-test.png", "image/png", png);
        String mediaId = media.path("id").asText();

        // 3. Real HTTP request to GET /api/v1/media/{id}/file without auth token succeeds (public permitAll)
        HttpRequest unauthenticatedGet = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/media/" + mediaId + "/file"))
                .GET()
                .build();
        HttpResponse<byte[]> publicResp = client.send(unauthenticatedGet, HttpResponse.BodyHandlers.ofByteArray());
        assertThat(publicResp.statusCode()).isEqualTo(200);
        assertThat(publicResp.body()).isEqualTo(png);

        // 4. Real HTTP request with valid Bearer token also succeeds
        HttpRequest authenticatedGet = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/media/" + mediaId + "/file"))
                .header("Authorization", "Bearer " + token())
                .GET()
                .build();
        HttpResponse<byte[]> authResp = client.send(authenticatedGet, HttpResponse.BodyHandlers.ofByteArray());
        assertThat(authResp.statusCode()).isEqualTo(200);
        assertThat(authResp.body()).isEqualTo(png);
    }

    @Test
    @DisplayName("Embedded Tomcat serves standards-compliant wire Content-Disposition headers for internationalized and special filenames")
    void servesRealTomcatWireHeadersForInternationalizedFilenames() throws Exception {
        List<TestCase> testCases = List.of(
                new TestCase("photo.png", "inline; filename=\"photo.png\""),
                new TestCase("café.png", "inline; filename=\"cafe.png\"; filename*=UTF-8''caf%C3%A9.png"),
                new TestCase("über.png", "inline; filename=\"uber.png\"; filename*=UTF-8''%C3%BCber.png"),
                new TestCase("封面.png", "inline; filename=\"image.png\"; filename*=UTF-8''%E5%B0%81%E9%9D%A2.png"),
                new TestCase("report\"final.png", "inline; filename=\"report\\\"final.png\"; filename*=UTF-8''report%22final.png")
        );

        byte[] png = TestImages.encode("png", 10, 10);

        for (TestCase tc : testCases) {
            // Upload media through authenticated API
            JsonNode media = upload(tc.filename(), "image/png", png);
            String fileUrl = media.path("url").asText();

            // Real HTTP/1.1 GET request to the running embedded Tomcat server
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + fileUrl))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo(png);

            String wireHeader = response.headers().firstValue("Content-Disposition").orElse(null);
            assertThat(wireHeader)
                    .as("Content-Disposition header must be present on the wire for %s", tc.filename())
                    .isNotNull();

            // 1. Must preserve inline disposition
            assertThat(wireHeader).startsWith("inline;");

            // 2. Must consist solely of printable ASCII characters [0x20, 0x7E] (never dropped by Tomcat)
            for (char c : wireHeader.toCharArray()) {
                assertThat((int) c)
                        .as("Header character '%c' must be printable ASCII in wire header for %s", c, tc.filename())
                        .isBetween(0x20, 0x7E);
            }

            // 3. Must match the exact standards-compliant header format
            assertThat(wireHeader).isEqualTo(tc.expectedHeader());

            // 4. ContentDisposition parser must extract the exact original filename
            ContentDisposition parsed = ContentDisposition.parse(wireHeader);
            assertThat(parsed.isInline()).isTrue();
            assertThat(parsed.getFilename()).isEqualTo(tc.filename());
        }
    }
}
