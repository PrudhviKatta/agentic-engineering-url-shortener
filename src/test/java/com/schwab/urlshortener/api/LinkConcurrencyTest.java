package com.schwab.urlshortener.api;

import com.schwab.urlshortener.api.dto.CreateLinkRequest;
import com.schwab.urlshortener.api.dto.LinkResponse;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:link-concurrency;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        })
class LinkConcurrencyTest {

    @LocalServerPort int port;
    private final TestRestTemplate rest = new TestRestTemplate();

    @Test
    void twentyConcurrentCreatesProduceTwentyDistinctCodesWithNoDropsOrDuplicates() throws Exception {
        int concurrency = 20;
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        List<Callable<ResponseEntity<LinkResponse>>> tasks = IntStream.range(0, concurrency)
                .<Callable<ResponseEntity<LinkResponse>>>mapToObj(i -> () -> rest.postForEntity(
                        "http://localhost:" + port + "/links",
                        new CreateLinkRequest("https://example.com/concurrency-test-" + i, null),
                        LinkResponse.class))
                .toList();
        List<Future<ResponseEntity<LinkResponse>>> futures = pool.invokeAll(tasks);
        pool.shutdown();

        List<ResponseEntity<LinkResponse>> responses = futures.stream()
                .map(f -> { try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); } })
                .toList();

        // Zero drops: every request produced a 201 with a body.
        assertThat(responses).allMatch(r -> r.getStatusCode().value() == 201 && r.getBody() != null);

        // Zero incorrect mappings/duplicates: 20 distinct codes, each round-tripping to its own URL.
        List<String> codes = responses.stream().map(r -> r.getBody().code()).collect(Collectors.toList());
        assertThat(codes).doesNotHaveDuplicates().hasSize(concurrency);
        for (int i = 0; i < concurrency; i++) {
            assertThat(responses.get(i).getBody().longUrl())
                    .isEqualTo("https://example.com/concurrency-test-" + i);
        }
    }

    @Test
    void concurrentOverlongSubmissionsAreAllRejected() throws Exception {
        int concurrency = 20;
        String overlongUrl = "https://example.com/" + "a".repeat(2_029);
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        List<Callable<ResponseEntity<String>>> tasks = IntStream.range(0, concurrency)
                .<Callable<ResponseEntity<String>>>mapToObj(i -> () -> rest.postForEntity(
                        "http://localhost:" + port + "/links",
                        new CreateLinkRequest(overlongUrl, null),
                        String.class))
                .toList();

        List<Future<ResponseEntity<String>>> futures = pool.invokeAll(tasks);
        pool.shutdown();

        List<ResponseEntity<String>> responses = futures.stream()
                .map(f -> { try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); } })
                .toList();
        assertThat(responses).allMatch(response ->
                response.getStatusCode().value() == 422
                        && response.getBody() != null
                        && response.getBody().contains("url_too_long"));
    }
}
