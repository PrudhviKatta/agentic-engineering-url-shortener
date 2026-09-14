// src/main/java/com/schwab/urlshortener/policy/PolicyCheckResult.java
package com.schwab.urlshortener.policy;

public record PolicyCheckResult(String policyId, PolicyOutcome outcome, String reason, boolean mandatory) { }
