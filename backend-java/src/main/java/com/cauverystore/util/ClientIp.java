package com.cauverystore.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The visitor's own internet address.
 *
 * On Railway every request reaches the app through Railway's proxy, so
 * {@code request.getRemoteAddr()} is the proxy's address for everyone - which made every
 * "too many attempts from this address" limit one shared limit for the whole store. The proxy
 * passes the real address on in X-Forwarded-For (X-Real-IP is only a fallback).
 *
 * X-Forwarded-For is read from the RIGHT: each proxy appends the address it saw, so the last
 * entry is the one Railway's proxy added and cannot be faked by the visitor; anything to its
 * left could have been typed in by the visitor.
 */
public final class ClientIp {

    private ClientIp() {}

    public static String of(HttpServletRequest request) {
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            for (int i = parts.length - 1; i >= 0; i--) {
                String candidate = clean(parts[i]);
                if (candidate != null) return candidate;
            }
        }
        String realIp = clean(request.getHeader("X-Real-IP"));
        if (realIp != null) return realIp;
        return request.getRemoteAddr();
    }

    /** The visitor's address for the request being handled on this thread, or null outside a request. */
    public static String current() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return of(servletAttrs.getRequest());
        }
        return null;
    }

    private static String clean(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty() || v.length() > 64 || "unknown".equalsIgnoreCase(v)) return null;
        return v;
    }
}
