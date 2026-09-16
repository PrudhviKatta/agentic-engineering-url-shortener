package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.policy.PolicyEngine;
import com.schwab.urlshortener.reliability.CompensationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrchestrationConfig {

    @Bean
    StageCatalog stageCatalog() {
        return BrownfieldWorkflowGraph.catalog();
    }

    @Bean
    PolicyEngine policyEngine() {
        return new PolicyEngine("policies/v1.yaml");
    }

    @Bean
    CompensationRegistry compensationRegistry() {
        return new CompensationRegistry();
    }
}
