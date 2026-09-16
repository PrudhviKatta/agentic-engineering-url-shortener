package com.schwab.urlshortener.domain;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Set;

public final class UrlValidator {

    private static final int MAX_URL_LENGTH = 2048;
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private UrlValidator() { }

    public static String validate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidUrlException("empty_url");
        }
        if (raw.length() > MAX_URL_LENGTH) {
            throw new InvalidUrlException("url_too_long");
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
                if (address instanceof Inet6Address v6) {
                    if (isUniqueLocalAddress(v6)) {
                        return true;
                    }
                    if (isDeprecatedIpv4Compatible(v6)) {
                        byte[] embeddedIpv4 = Arrays.copyOfRange(v6.getAddress(), 12, 16);
                        InetAddress mapped = InetAddress.getByAddress(embeddedIpv4);
                        if (mapped.isLoopbackAddress() || mapped.isLinkLocalAddress()
                                || mapped.isSiteLocalAddress() || mapped.isMulticastAddress()
                                || mapped.isAnyLocalAddress()) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (UnknownHostException e) {
            return true; // cannot resolve -> treat as unsafe/invalid
        }
    }

    private static boolean isUniqueLocalAddress(Inet6Address address) {
        return (address.getAddress()[0] & 0xfe) == 0xfc;
    }

    private static boolean isDeprecatedIpv4Compatible(Inet6Address address) {
        byte[] bytes = address.getAddress();
        for (int i = 0; i < 12; i++) {
            if (bytes[i] != 0) return false;
        }
        // Exclude ::/128 (unspecified) and ::1/128 (real IPv6 loopback) — both already handled
        // correctly by isAnyLocalAddress()/isLoopbackAddress() above; only the *deprecated*
        // ::a.b.c.d form (nonzero embedded IPv4) needs this separate check.
        return !(bytes[12] == 0 && bytes[13] == 0 && bytes[14] == 0 && (bytes[15] == 0 || bytes[15] == 1));
    }
}
