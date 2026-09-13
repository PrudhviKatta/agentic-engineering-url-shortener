package com.schwab.urlshortener.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LinkControllerContractTest {

    @LocalServerPort
    int port;

    private final TestRestTemplate rest = new TestRestTemplate();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void createLinkReturns201WithCode() {
        var request = Map.of("longUrl", "https://example.com/page");
        ResponseEntity<Map> resp = rest.postForEntity(url("/links"), request, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> body = resp.getBody();
        assertThat((String) body.get("code")).hasSize(7);
        assertThat(body.get("longUrl")).isEqualTo("https://example.com/page");
    }

    @Test
    void createLinkRejectsInvalidScheme() {
        var request = Map.of("longUrl", "javascript:alert(1)");
        ResponseEntity<Map> resp = rest.postForEntity(url("/links"), request, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        Map<?, ?> error = (Map<?, ?>) resp.getBody().get("error");
        assertThat((String) error.get("reason")).startsWith("scheme_not_allowed");
    }
}
