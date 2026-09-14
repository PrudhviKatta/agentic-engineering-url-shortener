// src/test/java/com/schwab/urlshortener/policy/PolicyEngineTest.java
package com.schwab.urlshortener.policy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyEngineTest {

    @Test
    void loadsVersionAndEvaluatesSecurityCheck() {
        PolicyEngine engine = new PolicyEngine("policies/v1.yaml");
        assertThat(engine.version()).isEqualTo("1.0.0");

        List<PolicyCheckResult> results = engine.evaluate("implementation", Map.of("urlSchemeAllowlistEnforced", true));
        PolicyCheckResult security = results.stream().filter(r -> r.policyId().equals("SEC-001")).findFirst().orElseThrow();
        assertThat(security.outcome()).isEqualTo(PolicyOutcome.PASS);
    }

    @Test
    void failsWhenControlAbsent() {
        PolicyEngine engine = new PolicyEngine("policies/v1.yaml");
        List<PolicyCheckResult> results = engine.evaluate("implementation", Map.of("urlSchemeAllowlistEnforced", false));
        PolicyCheckResult security = results.stream().filter(r -> r.policyId().equals("SEC-001")).findFirst().orElseThrow();
        assertThat(security.outcome()).isEqualTo(PolicyOutcome.FAIL);
    }

    @Test
    void notApplicableWhenCheckKeyMissingFromContext() {
        PolicyEngine engine = new PolicyEngine("policies/v1.yaml");
        List<PolicyCheckResult> results = engine.evaluate("implementation", Map.of());
        PolicyCheckResult security = results.stream().filter(r -> r.policyId().equals("SEC-001")).findFirst().orElseThrow();
        assertThat(security.outcome()).isEqualTo(PolicyOutcome.NOT_APPLICABLE);
    }

    @Test
    void returnsEmptyListWhenNoPolicyMatchesTheStage() {
        PolicyEngine engine = new PolicyEngine("policies/v1.yaml");
        List<PolicyCheckResult> results = engine.evaluate("no_such_stage", Map.of());
        assertThat(results).isEmpty();
    }

    @Test
    void nonBooleanCheckValueIsTreatedAsFail() {
        PolicyEngine engine = new PolicyEngine("policies/v1.yaml");
        List<PolicyCheckResult> results = engine.evaluate("implementation", Map.of("urlSchemeAllowlistEnforced", "yes"));
        PolicyCheckResult security = results.stream().filter(r -> r.policyId().equals("SEC-001")).findFirst().orElseThrow();
        assertThat(security.outcome()).isEqualTo(PolicyOutcome.FAIL);
    }
}
