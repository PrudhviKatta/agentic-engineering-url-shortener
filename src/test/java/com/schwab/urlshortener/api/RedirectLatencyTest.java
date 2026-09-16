package com.schwab.urlshortener.api;

import com.schwab.urlshortener.persistence.ShortLinkEntity;
import com.schwab.urlshortener.persistence.ShortLinkJpaRepository;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:redirect-latency;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        })
class RedirectLatencyTest {

    private static final int SAMPLE_SIZE = 100;
    private static final double DEMONSTRATION_P95_LIMIT_MILLIS = 200.0;

    @LocalServerPort
    int port;

    @Autowired
    ShortLinkJpaRepository links;

    @Test
    void demonstrationP95RedirectLatencyStaysWithinTwoHundredMilliseconds() {
        ShortLinkEntity entity = new ShortLinkEntity();
        entity.setCode("LATENCY");
        entity.setLongUrl("https://example.com/latency-target");
        links.save(entity);

        TestRestTemplate client = noRedirectClient();
        String endpoint = "http://localhost:" + port + "/r/LATENCY";

        for (int i = 0; i < 10; i++) {
            assertThat(client.getForEntity(endpoint, Void.class).getStatusCode().value()).isEqualTo(307);
        }

        List<Double> latencyMillis = new ArrayList<>(SAMPLE_SIZE);
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            long started = System.nanoTime();
            ResponseEntity<Void> response = client.getForEntity(endpoint, Void.class);
            latencyMillis.add((System.nanoTime() - started) / 1_000_000.0);
            assertThat(response.getStatusCode().value()).isEqualTo(307);
        }

        Collections.sort(latencyMillis);
        int p95Index = (int) Math.ceil(SAMPLE_SIZE * 0.95) - 1;
        double p95Millis = latencyMillis.get(p95Index);
        System.out.printf(
                "DEMONSTRATION_P95_REDIRECT_LATENCY_MS=%.3f SAMPLE_SIZE=%d%n",
                p95Millis, SAMPLE_SIZE);

        assertThat(p95Millis).isLessThanOrEqualTo(DEMONSTRATION_P95_LIMIT_MILLIS);
    }

    private TestRestTemplate noRedirectClient() {
        var factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod)
                    throws java.io.IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        };
        var client = new TestRestTemplate();
        client.getRestTemplate().setRequestFactory(factory);
        return client;
    }
}
