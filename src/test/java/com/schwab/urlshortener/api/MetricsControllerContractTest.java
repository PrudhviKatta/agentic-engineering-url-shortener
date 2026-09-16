package com.schwab.urlshortener.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.urlshortener.scenarios.ScenarioRunner;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MetricsControllerContractTest {

    @LocalServerPort private int port;
    @Autowired private ScenarioRunner scenarioRunner;
    @Autowired private ObjectMapper objectMapper;
    private final TestRestTemplate rest = new TestRestTemplate();

    @Test
    void httpAndCliExposeTheSameDemonstrationLabeledSummary() throws Exception {
        ResponseEntity<String> response = rest.getForEntity(
                "http://localhost:" + port + "/metrics", String.class);

        ByteArrayOutputStream cliBytes = new ByteArrayOutputStream();
        scenarioRunner.printMetricsReport(
                new PrintStream(cliBytes, true, StandardCharsets.UTF_8));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode httpJson = objectMapper.readTree(response.getBody());
        JsonNode cliJson = objectMapper.readTree(cliBytes.toString(StandardCharsets.UTF_8));
        assertThat(cliJson).isEqualTo(httpJson);
        assertThat(httpJson.path("isDemonstrationData").asBoolean()).isTrue();
        assertThat(httpJson.has("successRate")).isTrue();
        assertThat(httpJson.has("failureRate")).isTrue();
        assertThat(httpJson.has("retryFrequency")).isTrue();
        assertThat(httpJson.has("rollbackOrCompensationFrequency")).isTrue();
        assertThat(httpJson.has("fallbackFrequency")).isTrue();
        assertThat(httpJson.path("mttr").has("recoveredCount")).isTrue();
        assertThat(httpJson.path("mttr").has("unrecoveredCount")).isTrue();
        assertThat(httpJson.path("mttr").path("populationNote").asText())
                .containsIgnoringCase("demonstration");
    }
}
