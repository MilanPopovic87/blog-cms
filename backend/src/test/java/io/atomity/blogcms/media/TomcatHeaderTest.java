package io.atomity.blogcms.media;

import io.atomity.blogcms.media.controller.MediaController;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.ContentDisposition;
import org.springframework.web.util.UriUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class TomcatHeaderTest {

    private static Tomcat tomcat;
    private static int port;
    private static HttpClient client;

    @BeforeAll
    static void startTomcat() throws Exception {
        tomcat = new Tomcat();
        tomcat.setPort(0);
        tomcat.getConnector();

        File baseDir = File.createTempFile("tomcat", "tmp");
        baseDir.delete();
        baseDir.mkdirs();
        baseDir.deleteOnExit();
        tomcat.setBaseDir(baseDir.getAbsolutePath());

        Context context = tomcat.addContext("", baseDir.getAbsolutePath());

        Tomcat.addServlet(context, "testServlet", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                String name = req.getParameter("name");
                String header = MediaController.contentDispositionHeader(name);
                resp.setHeader("Content-Disposition", header);
                resp.setContentType("image/png");
                resp.getWriter().write("ok");
            }
        });
        context.addServletMappingDecoded("/media/file", "testServlet");

        tomcat.start();
        port = tomcat.getConnector().getLocalPort();
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stopTomcat() throws Exception {
        if (tomcat != null) {
            tomcat.stop();
            tomcat.destroy();
        }
    }

    @Test
    void testTomcatWireHeadersForRequiredFilenames() throws Exception {
        String[] testNames = {
                "photo.png",
                "café.png",
                "über.png",
                "封面.png",
                "report\"final.png"
        };

        for (String originalName : testNames) {
            String encodedQuery = UriUtils.encode(originalName, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/media/file?name=" + encodedQuery))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.ISO_8859_1));

            assertThat(resp.statusCode()).isEqualTo(200);
            String header = resp.headers().firstValue("Content-Disposition").orElse(null);
            assertThat(header).isNotNull();

            System.out.println("Original: [" + originalName + "]");
            System.out.println("  Wire Header: [" + header + "]");

            // 1. Must preserve inline
            assertThat(header).startsWith("inline;");

            // 2. Must only contain ASCII printable characters on the wire (no Tomcat header drop)
            for (char c : header.toCharArray()) {
                assertThat((int) c).isBetween(0x20, 0x7E);
            }

            // 3. Spring ContentDisposition parser must extract the exact original filename
            ContentDisposition parsed = ContentDisposition.parse(header);
            assertThat(parsed.isInline()).isTrue();
            assertThat(parsed.getFilename()).isEqualTo(originalName);
        }
    }
}
