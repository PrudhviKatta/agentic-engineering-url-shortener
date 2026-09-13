package com.schwab.urlshortener.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthControllerTest {

    @LocalServerPort
    int port;

    private final TestRestTemplate rest = new TestRestTemplate();

    @Test
    void healthzReturnsOk() {
        ResponseEntity<String> resp = rest.getForEntity("http://localhost:" + port + "/healthz", String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).contains("\"status\":\"ok\"");
    }

    @Test
    void readyzReturnsReadyWhenDbReachable() {
        ResponseEntity<String> resp = rest.getForEntity("http://localhost:" + port + "/readyz", String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).contains("\"status\":\"ready\"");
    }
}
