package com.schwab.urlshortener.observability.dto;

public record ApprovalRequest(String decision, String actor, String reason) { }
