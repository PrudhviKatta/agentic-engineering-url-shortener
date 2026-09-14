package com.schwab.urlshortener.scenarios;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.urlshortener.orchestration.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

// @DataJpaTest wraps each test in a transaction on the test thread's own connection.
// WorkflowEngine.submit (invoked from ScenarioRunner.runScenario) dispatches stage
// handlers onto its internal thread pool, which borrows a *different* connection from
// the same DataSource; without this override those worker threads cannot see the test
// thread's uncommitted inserts (e.g. the WorkflowStepEntity rows submit() just wrote),
// and lookups by findByWorkflowId... throw NoSuchElementException. Same root cause and
// same fix as Tasks 22/23/24's WorkflowEngine tests - not a ScenarioRunner or
// WorkflowEngine bug.
@DataJpaTest
@Import(ScenarioRunner.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ScenarioRunnerSmokeTest {

    @Autowired private ScenarioRunner runner;

    @Test
    void cliRunsAndPrintsJsonLinesForGreenfield() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        runner.runScenario("greenfield", new PrintStream(buffer, true, StandardCharsets.UTF_8));

        List<String> lines = buffer.toString(StandardCharsets.UTF_8).lines()
                .filter(l -> !l.isBlank()).toList();
        assertThat(lines).isNotEmpty();
        new ObjectMapper().readTree(lines.get(lines.size() - 1)); // last line must be valid JSON (final summary)
    }
}
