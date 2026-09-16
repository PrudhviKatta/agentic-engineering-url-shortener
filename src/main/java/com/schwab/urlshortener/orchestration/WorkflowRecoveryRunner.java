package com.schwab.urlshortener.orchestration;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Resumes durable non-terminal workflows whenever the application starts. */
@Component
@Order(0)
public final class WorkflowRecoveryRunner implements CommandLineRunner {
    private final WorkflowEngine engine;

    public WorkflowRecoveryRunner(WorkflowEngine engine) {
        this.engine = engine;
    }

    @Override
    public void run(String... args) {
        engine.resumeAll();
    }
}
