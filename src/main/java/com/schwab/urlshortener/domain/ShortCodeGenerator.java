package com.schwab.urlshortener.domain;

public final class ShortCodeGenerator {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = ALPHABET.length();
    private static final int DEFAULT_WIDTH = 7;

    private ShortCodeGenerator() { }

    public static String toBase62(long n) {
        if (n == 0) {
            return String.valueOf(ALPHABET.charAt(0));
        }
        StringBuilder digits = new StringBuilder();
        while (n > 0) {
            digits.append(ALPHABET.charAt((int) (n % BASE)));
            n /= BASE;
        }
        return digits.reverse().toString();
    }

    public static String generateCode(long numericId) {
        return generateCode(numericId, DEFAULT_WIDTH);
    }

    public static String generateCode(long numericId, int width) {
        String encoded = toBase62(numericId);
        return "0".repeat(Math.max(0, width - encoded.length())) + encoded;
    }
}
