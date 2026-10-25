package io.atomity.blogcms.shared;

import io.atomity.blogcms.IntegrationTest;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Parallel creates with the same slug can all pass the service's existsBySlug pre-check;
 * the unique constraint then decides, and the losers must get 409 SLUG_TAKEN, never a 500.
 */
class SlugRaceApiTest extends IntegrationTest {

    private static final int THREADS = 8;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @RepeatedTest(3)
    void parallelPostCreatesWithTheSameSlug() throws Exception {
        List<String> outcomes = createInParallel("/api/v1/posts",
                Map.of("title", "Race", "slug", "race-test", "content", Map.of("type", "doc")));

        assertOneCreatedRestSlugTaken(outcomes);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM posts WHERE slug = 'race-test'", Integer.class))
                .isEqualTo(1);
    }

    @RepeatedTest(3)
    void parallelTagCreatesWithTheSameDerivedSlug() throws Exception {
        List<String> outcomes = createInParallel("/api/v1/tags", Map.of("name", "Race tag", "slug", ""));

        assertOneCreatedRestSlugTaken(outcomes);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM tags WHERE slug = 'race-tag'", Integer.class))
                .isEqualTo(1);
    }

    /** Sends the same create from THREADS threads at once; returns "201" or "<status> <errorCode>" per request. */
    private List<String> createInParallel(String path, Map<String, Object> body) throws Exception {
        token(); // fetched once here, so the workers only read the cached token
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return call(post(path), body).andReturn();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<String> outcomes = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                outcomes.add(outcome(future.get(30, TimeUnit.SECONDS)));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private String outcome(MvcResult result) throws Exception {
        int status = result.getResponse().getStatus();
        if (status == 201) {
            return "201";
        }
        String errorCode = objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/errors/0/errorCode").asText();
        return status + " " + errorCode;
    }

    private static void assertOneCreatedRestSlugTaken(List<String> outcomes) {
        assertThat(Collections.frequency(outcomes, "201")).as("outcomes: %s", outcomes).isEqualTo(1);
        assertThat(Collections.frequency(outcomes, "409 SLUG_TAKEN")).as("outcomes: %s", outcomes).isEqualTo(THREADS - 1);
    }
}
