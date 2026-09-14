// src/main/java/com/schwab/urlshortener/policy/PolicyDefinition.java
package com.schwab.urlshortener.policy;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PolicyDefinition(
        String id, String domain, String stage, boolean mandatory, String description,
        @JsonProperty("check_key") String checkKey
) { }
