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

    public PolicyEngine(String classpathLocation) {
        try (InputStream in = new ClassPathResource(classpathLocation).getInputStream()) {
            PolicyDocument doc = new YAMLMapper().readValue(in, PolicyDocument.class);
            this.version = doc.version();
            this.policies = doc.policies();
        } catch (IOException e) {
            throw new IllegalStateException("failed to load policy file: " + classpathLocation, e);
        }
    }

    public String version() { return version; }

    public List<PolicyCheckResult> evaluate(String stageName, Map<String, Object> context) {
        List<PolicyCheckResult> results = new ArrayList<>();
        for (PolicyDefinition policy : policies) {
            if (!policy.stage().equals(stageName)) continue;
            String checkKey = policy.checkKey();
            PolicyOutcome outcome;
            String reason;
            if (!context.containsKey(checkKey)) {
                outcome = PolicyOutcome.NOT_APPLICABLE;
                reason = checkKey + " not evaluated in this context";
            } else if (Boolean.TRUE.equals(context.get(checkKey))) {
                outcome = PolicyOutcome.PASS;
                reason = "control confirmed present";
            } else {
                outcome = PolicyOutcome.FAIL;
                reason = "control not confirmed present";
            }
            results.add(new PolicyCheckResult(policy.id(), outcome, reason, policy.mandatory()));
        }
        return results;
    }
}
