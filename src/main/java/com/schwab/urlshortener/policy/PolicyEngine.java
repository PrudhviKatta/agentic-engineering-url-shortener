// src/main/java/com/schwab/urlshortener/policy/PolicyEngine.java
package com.schwab.urlshortener.policy;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;

public class PolicyEngine {

    private record PolicyDocument(String version, List<PolicyDefinition> policies) { }

    private final String version;
    private final List<PolicyDefinition> policies;

    // Codex REQUEST AMENDMENTS item 3: a context entry named "<checkKey>.exceptionRequested" ==
    // Boolean.TRUE is the ONLY legitimate way evaluate() produces EXCEPTION_REQUESTED — a stage
    // handler that knows a control isn't met and is explicitly flagging that it's requesting a
    // policy exception for it (as opposed to silently failing). This mirrors applicabilityKey's
    // "explicit signal, not silent inference" design.
    private static final String EXCEPTION_REQUESTED_SUFFIX = ".exceptionRequested";

    public PolicyEngine(String classpathLocation) {
        try (InputStream in = new ClassPathResource(classpathLocation).getInputStream()) {
            PolicyDocument doc = new YAMLMapper().readValue(in, PolicyDocument.class);
            if (doc.policies() == null) {
                throw new IllegalStateException("policy file has no 'policies' list: " + classpathLocation);
            }
            this.version = doc.version();
            this.policies = doc.policies();
        } catch (IOException e) {
            throw new IllegalStateException("failed to load policy file: " + classpathLocation, e);
        }
    }

    // Package-private: testing-only constructor to exercise the full outcome matrix without
    // polluting the production v1.yaml with a policy that exists only to prove a mechanism works.
    PolicyEngine(String version, List<PolicyDefinition> policies) {
        this.version = version;
        this.policies = List.copyOf(policies);
    }

    public String version() { return version; }

    public List<PolicyCheckResult> evaluate(String stageName, Map<String, Object> context) {
        List<PolicyCheckResult> results = new ArrayList<>();
        for (PolicyDefinition policy : policies) {
            if (!policy.stage().equals(stageName)) continue;
            PolicyOutcome outcome;
            String reason;

            if (policy.applicabilityKey() != null && Boolean.FALSE.equals(context.get(policy.applicabilityKey()))) {
                // The ONLY legitimate NOT_APPLICABLE path: an explicit applicability signal, not
                // an absent/unrelated context key.
                outcome = PolicyOutcome.NOT_APPLICABLE;
                reason = "not applicable to this run: " + policy.applicabilityKey() + " is false";
            } else {
                String checkKey = policy.checkKey();
                Object evidence = context.get(checkKey);
                if (Boolean.TRUE.equals(evidence)) {
                    outcome = PolicyOutcome.PASS;
                    reason = "control confirmed present";
                } else if (Boolean.TRUE.equals(context.get(checkKey + EXCEPTION_REQUESTED_SUFFIX))) {
                    outcome = PolicyOutcome.EXCEPTION_REQUESTED;
                    reason = checkKey + " not confirmed present; exception explicitly requested";
                } else {
                    // Codex REQUEST AMENDMENTS item 3: missing, false, AND non-Boolean evidence
                    // all resolve here — a matched policy never silently stops being enforced just
                    // because nobody reported evidence for it. Absence of proof is not proof of
                    // absence, but it is also never treated as "doesn't apply."
                    outcome = PolicyOutcome.FAIL;
                    reason = evidence == null
                            ? checkKey + " not reported in this context"
                            : checkKey + " not confirmed present (value: " + evidence + ")";
                }
            }
            results.add(new PolicyCheckResult(policy.id(), outcome, reason, policy.mandatory()));
        }
        return results;
    }
}
