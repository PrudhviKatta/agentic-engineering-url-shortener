// src/main/java/com/schwab/urlshortener/policy/PolicyDefinition.java
package com.schwab.urlshortener.policy;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PolicyDefinition(
        String id, String domain, String stage, boolean mandatory, String description,
        @JsonProperty("check_key") String checkKey,
        // Independent review amendments item 3: the ONLY legitimate way a matched policy becomes
        // NOT_APPLICABLE. Nullable — most policies apply unconditionally to every run of their
        // stage. When set, evaluate() checks this key in the context (not checkKey): if it's
        // explicitly Boolean.FALSE, the policy doesn't apply to this run at all and checkKey is
        // never consulted. Missing/false/non-Boolean checkKey evidence is never NOT_APPLICABLE by
        // itself — that would let a policy silently stop being enforced just because nobody
        // reported evidence for it, which is exactly the gap this field closes.
        @JsonProperty("applicability_key") String applicabilityKey
) { }
