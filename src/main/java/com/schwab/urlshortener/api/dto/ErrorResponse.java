package com.schwab.urlshortener.api.dto;

public record ErrorResponse(ErrorDetail error) {
    public record ErrorDetail(String reason) { }

    public static ErrorResponse of(String reason) {
        return new ErrorResponse(new ErrorDetail(reason));
    }
}
