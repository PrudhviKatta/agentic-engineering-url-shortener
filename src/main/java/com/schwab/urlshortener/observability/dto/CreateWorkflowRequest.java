package com.schwab.urlshortener.observability.dto;

// Matches openapi.yaml's CreateWorkflowRequest schema exactly: {feature, requirementText}, both
// required.
public record CreateWorkflowRequest(String feature, String requirementText) { }
