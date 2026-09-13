package com.schwab.urlshortener.domain;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Set;

public final class UrlValidator {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private UrlValidator() { }

    public static String validate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidUrlException("empty_url");
        }
        URI uri;
        try {
            uri = new URI(raw);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("malformed_url");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new InvalidUrlException("scheme_not_allowed:" + scheme);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidUrlException("missing_host");
        }
        if (host.equalsIgnoreCase("localhost") || isBlockedHost(host)) {
            throw new InvalidUrlException("target_host_blocked");
        }
        return raw;
    }

    private static boolean isBlockedHost(String host) {
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()
                        || address.isAnyLocalAddress()) {
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException e) {
            return true; // cannot resolve -> treat as unsafe/invalid
        }
    }
}
