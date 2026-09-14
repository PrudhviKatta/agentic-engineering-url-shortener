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
    void mandatoryPolicyWithMissingEvidenceFailsRatherThanBeingNotApplicable() {
        // Codex REQUEST AMENDMENTS item 3: a matched, mandatory policy must never silently stop
        // being enforced just because nobody reported evidence for it. This test previously
        // asserted NOT_APPLICABLE here, which was exactly the defect this correction fixes.
        PolicyEngine engine = new PolicyEngine("policies/v1.yaml");
        List<PolicyCheckResult> results = engine.evaluate("implementation", Map.of());
        PolicyCheckResult security = results.stream().filter(r -> r.policyId().equals("SEC-001")).findFirst().orElseThrow();
        assertThat(security.outcome()).isEqualTo(PolicyOutcome.FAIL);
    }

    @Test
    void notApplicableOnlyViaExplicitApplicabilityKey() {
        PolicyDefinition conditional = new PolicyDefinition("COND-001", "test", "implementation", true,
                "only applies when a feature flag is on", "someControlEnforced", "featureFlagEnabled");
        PolicyEngine engine = new PolicyEngine("test-1.0", List.of(conditional));

        // applicabilityKey explicitly false: NOT_APPLICABLE, checkKey never even consulted (it's
        // absent from the context here, which would otherwise mean FAIL).
        List<PolicyCheckResult> results = engine.evaluate("implementation", Map.of("featureFlagEnabled", false));
        assertThat(results.get(0).outcome()).isEqualTo(PolicyOutcome.NOT_APPLICABLE);

        // applicabilityKey absent entirely (not explicitly false): the policy still applies by
        // default, so missing checkKey evidence blocks as usual (FAIL).
        List<PolicyCheckResult> resultsNoFlag = engine.evaluate("implementation", Map.of());
        assertThat(resultsNoFlag.get(0).outcome()).isEqualTo(PolicyOutcome.FAIL);
    }

    @Test
    void exceptionRequestedIsReachableViaAnExplicitContextSignal() {
        PolicyDefinition policy = new PolicyDefinition("EXC-001", "test", "implementation", true,
                "control with a requestable exception", "someControlEnforced", null);
        PolicyEngine engine = new PolicyEngine("test-1.0", List.of(policy));

        List<PolicyCheckResult> results = engine.evaluate("implementation",
                Map.of("someControlEnforced.exceptionRequested", true));
        assertThat(results.get(0).outcome()).isEqualTo(PolicyOutcome.EXCEPTION_REQUESTED);
    }

    @Test
    void allFourOutcomesAreDistinguishable() {
        PolicyDefinition policy = new PolicyDefinition("ALL-001", "test", "implementation", true,
                "exercised across all four outcomes", "controlKey", "applicabilityFlag");
        PolicyEngine engine = new PolicyEngine("test-1.0", List.of(policy));

        assertThat(engine.evaluate("implementation", Map.of("controlKey", true)).get(0).outcome())
                .isEqualTo(PolicyOutcome.PASS);
        assertThat(engine.evaluate("implementation", Map.of()).get(0).outcome())
                .isEqualTo(PolicyOutcome.FAIL);
        assertThat(engine.evaluate("implementation", Map.of("controlKey.exceptionRequested", true)).get(0).outcome())
                .isEqualTo(PolicyOutcome.EXCEPTION_REQUESTED);
        assertThat(engine.evaluate("implementation", Map.of("applicabilityFlag", false)).get(0).outcome())
                .isEqualTo(PolicyOutcome.NOT_APPLICABLE);
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
