package com.cauverystore.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;

/**
 * The refresh token cookie.
 *
 * <h2>Why SameSite is decided per request rather than read straight from configuration</h2>
 *
 * The site is deployed across two registrable domains: the frontend on cauverystore.in and the
 * API on railway.app. A cookie sent with SameSite=Lax is simply never attached to a cross-site
 * XHR, so on that deployment /api/auth/refresh cannot see the refresh token at all. Every signed
 * in user is thrown back to the login screen the moment their access token expires, with the
 * session-expired banner, no matter how long the refresh token is valid for.
 *
 * That used to depend on AUTH_COOKIE_SAMESITE=none being set in the deployment environment. It is
 * the worst kind of setting: invisible when missing, correct-looking in code, and it breaks
 * authentication for everybody rather than failing at startup. The request already carries the
 * answer - Origin says where the browser is, Host says where we are - so it is worked out here
 * instead of being asked for.
 *
 * Configuration is still honoured, and is still what applies when there is no request to read
 * (and for anyone deploying same-site). It is only ever upgraded, never weakened: a deployment
 * that has correctly set None keeps it.
 */
@Component
public class CookieUtil {

    private static final Logger log = LoggerFactory.getLogger(CookieUtil.class);

    public static final String REFRESH_COOKIE_NAME = "refresh_token";

    @Value("${auth.cookie.secure:false}")
    private boolean secure;

    @Value("${auth.cookie.samesite:lax}")
    private String sameSite;

    @Value("${auth.cookie.max-age-seconds:604800}")
    private int maxAgeSeconds;

    /** Logged once rather than on every sign-in, so it reads as a deployment note, not noise. */
    private volatile boolean crossSiteReported = false;

    public void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            return;
        }
        response.addCookie(build(refreshToken, maxAgeSeconds));
    }

    public void clearRefreshTokenCookie(HttpServletResponse response) {
        // Must carry the same SameSite and Secure attributes as the cookie it is replacing.
        // A browser treats a differing pair as a different cookie and leaves the original in
        // place, which would make signing out fail to actually end the session.
        response.addCookie(build("", 0));
    }

    public String readRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (REFRESH_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private Cookie build(String value, int maxAge) {
        String effectiveSameSite = sameSite;
        boolean effectiveSecure = secure;

        if (isCrossSiteRequest() && !"none".equalsIgnoreCase(sameSite)) {
            // None is the only value a browser will send cross-site, and it is rejected unless
            // the cookie is also Secure - so the two move together.
            effectiveSameSite = "None";
            effectiveSecure = true;
            if (!crossSiteReported) {
                crossSiteReported = true;
                log.warn("Refresh cookie: request is cross-site, so SameSite=None; Secure is being "
                        + "used instead of the configured SameSite={}. Set AUTH_COOKIE_SAMESITE=none "
                        + "and AUTH_COOKIE_SECURE=true to make this explicit.", sameSite);
            }
        }

        if ("none".equalsIgnoreCase(effectiveSameSite)) {
            effectiveSecure = true;
        }

        Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(effectiveSecure);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        cookie.setAttribute("SameSite", effectiveSameSite);
        return cookie;
    }

    /**
     * Whether the browser making this request is on a different site from us.
     *
     * Compared on the registrable domain, not the host: api.example.com and www.example.com are
     * the same site and need no relaxation, while cauverystore.in and railway.app are not. Port
     * and scheme are deliberately ignored, because SameSite does not consider them - which is why
     * localhost:3000 calling localhost:9091 stays same-site and keeps the stricter Lax cookie.
     */
    private boolean isCrossSiteRequest() {
        HttpServletRequest request = currentRequest();
        if (request == null) return false;

        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            // No Origin header means this was not a cross-origin browser request, so there is
            // nothing to relax for.
            return false;
        }

        String originSite = registrableDomain(hostOf(origin));
        String ourSite = registrableDomain(request.getServerName());
        if (originSite == null || ourSite == null) return false;

        return !originSite.equalsIgnoreCase(ourSite);
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        return (attrs instanceof ServletRequestAttributes servlet) ? servlet.getRequest() : null;
    }

    private String hostOf(String origin) {
        try {
            return URI.create(origin).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Last two labels of a host.
     *
     * A deliberate approximation of the public suffix list, which would mean shipping and
     * refreshing that list for one comparison. It is wrong for multi-label suffixes such as
     * co.in, where it would call two unrelated sites the same. The consequence of that error is
     * that a cookie stays Lax on a deployment that needed None, which is the behaviour we already
     * have today - so the approximation can only leave things as they are, never make them worse.
     */
    private String registrableDomain(String host) {
        if (host == null || host.isBlank()) return null;
        String h = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        String[] labels = h.split("\\.");
        if (labels.length <= 2) return h;
        return labels[labels.length - 2] + "." + labels[labels.length - 1];
    }
}
