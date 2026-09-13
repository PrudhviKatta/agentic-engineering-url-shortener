package com.schwab.urlshortener.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.time.Instant;
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

    /**
     * TestRestTemplate follows redirects by default; this client disables that so
     * the raw 307/404/410 response can be inspected directly.
     */
    private TestRestTemplate noRedirectClient() {
        var factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws java.io.IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        };
        var restNoRedirect = new TestRestTemplate();
        restNoRedirect.getRestTemplate().setRequestFactory(factory);
        return restNoRedirect;
    }

    @Test
    void redirectFollowsToLongUrl() {
        var created = rest.postForEntity(url("/links"), Map.of("longUrl", "https://example.com/target"), Map.class);
        String code = (String) created.getBody().get("code");

        ResponseEntity<Void> resp = noRedirectClient().getForEntity(url("/r/" + code), Void.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(307);
        assertThat(resp.getHeaders().getLocation().toString()).isEqualTo("https://example.com/target");
    }

    @Test
    void redirectUnknownCodeReturns404() {
        ResponseEntity<Void> resp = noRedirectClient().getForEntity(url("/r/ZZZZZZZ"), Void.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void redirectExpiredCodeReturns410() {
        var past = Instant.now().minus(Duration.ofDays(1));
        var created = rest.postForEntity(url("/links"), Map.of("longUrl", "https://example.com/x", "expiresAt", past.toString()), Map.class);
        String code = (String) created.getBody().get("code");

        ResponseEntity<Void> resp = noRedirectClient().getForEntity(url("/r/" + code), Void.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(410);
    }

    @Test
    void analyticsReflectsRedirectCount() {
        var created = rest.postForEntity(url("/links"), Map.of("longUrl", "https://example.com/a"), Map.class);
        String code = (String) created.getBody().get("code");
        rest.getForEntity(url("/r/" + code), String.class);
        rest.getForEntity(url("/r/" + code), String.class);

        ResponseEntity<Map> analytics = rest.getForEntity(url("/links/" + code + "/analytics"), Map.class);
        assertThat(((Number) analytics.getBody().get("redirectCount")).intValue()).isEqualTo(2);
    }
}
