package com.schwab.urlshortener.orchestration;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.datasource.url=jdbc:h2:mem:recovery-boot;DB_CLOSE_DELAY=-1")
class WorkflowRecoveryBootTest {

    @MockBean private WorkflowEngine workflowEngine;

    @Test
    void applicationBootInvokesPersistedWorkflowRecovery() {
        verify(workflowEngine).resumeAll();
    }
}
