package com.cauverystore.util;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The refresh cookie decides whether anybody stays signed in.
 *
 * The failure these tests exist for produced no error anywhere: the cookie was set, the login
 * succeeded, and then the browser quietly declined to send it back to an API on another domain.
 * Users saw "Your session expired" on the home page and nothing was wrong in any log.
 */
class CookieUtilTest {

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    /** A CookieUtil configured the way it is by default - the localhost-shaped settings. */
    private CookieUtil defaultsConfigured() {
        CookieUtil util = new CookieUtil();
        ReflectionTestUtils.setField(util, "secure", false);
        ReflectionTestUtils.setField(util, "sameSite", "lax");
        ReflectionTestUtils.setField(util, "maxAgeSeconds", 604800);
        return util;
    }

    private void currentRequestFrom(String origin, String serverName) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName(serverName);
        if (origin != null) request.addHeader("Origin", origin);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private Cookie issue(CookieUtil util) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        util.setRefreshTokenCookie(response, "a-refresh-token");
        Cookie cookie = response.getCookie(CookieUtil.REFRESH_COOKIE_NAME);
        assertNotNull(cookie, "no refresh cookie was set");
        return cookie;
    }

    @Test
    void theProductionDeploymentGetsACookieTheBrowserWillActuallySendBack() {
        // cauverystore.in calling railway.app. This is the live arrangement, and with the
        // configured Lax the browser never returns the cookie, so refresh can only ever fail.
        currentRequestFrom("https://cauverystore.in", "cauvery-store-backend-production.up.railway.app");

        Cookie cookie = issue(defaultsConfigured());

        assertEquals("None", cookie.getAttribute("SameSite"));
        assertTrue(cookie.getSecure(), "SameSite=None is ignored by browsers unless Secure is set");
    }

    @Test
    void localhostDevelopmentKeepsTheStricterCookie() {
        // Different ports are still the same site. Nothing needs relaxing here, and quietly
        // switching to Secure would break development over plain http.
        currentRequestFrom("http://localhost:3000", "localhost");

        Cookie cookie = issue(defaultsConfigured());

        assertEquals("lax", cookie.getAttribute("SameSite"));
        assertFalse(cookie.getSecure());
    }

    @Test
    void subdomainsOfOneSiteAreNotTreatedAsCrossSite() {
        currentRequestFrom("https://www.cauverystore.in", "api.cauverystore.in");

        assertEquals("lax", issue(defaultsConfigured()).getAttribute("SameSite"));
    }

    @Test
    void anExplicitNoneIsHonouredAndForcedSecure() {
        // A deployment that already set this correctly must not be downgraded.
        CookieUtil util = defaultsConfigured();
        ReflectionTestUtils.setField(util, "sameSite", "none");
        currentRequestFrom("http://localhost:3000", "localhost");

        Cookie cookie = issue(util);

        assertEquals("none", cookie.getAttribute("SameSite"));
        assertTrue(cookie.getSecure());
    }

    @Test
    void aRequestWithNoOriginChangesNothing() {
        // Server-to-server calls and same-origin navigations send no Origin. There is no
        // cross-site problem to solve, so configuration stands.
        currentRequestFrom(null, "cauverystore.in");

        assertEquals("lax", issue(defaultsConfigured()).getAttribute("SameSite"));
    }

    @Test
    void theLogoutCookieMatchesTheOneItIsMeantToReplace() {
        // A browser keys a cookie partly on these attributes. Clearing with a different pair
        // leaves the original in place and the session never actually ends.
        currentRequestFrom("https://cauverystore.in", "cauvery-store-backend-production.up.railway.app");

        MockHttpServletResponse response = new MockHttpServletResponse();
        defaultsConfigured().clearRefreshTokenCookie(response);
        Cookie cleared = response.getCookie(CookieUtil.REFRESH_COOKIE_NAME);

        assertNotNull(cleared);
        assertEquals(0, cleared.getMaxAge());
        assertEquals("None", cleared.getAttribute("SameSite"));
        assertTrue(cleared.getSecure());
    }

    @Test
    void noRequestContextFallsBackToConfigurationRatherThanFailing() {
        // Scheduled jobs and tests have no request bound. This must not throw.
        RequestContextHolder.resetRequestAttributes();

        assertEquals("lax", issue(defaultsConfigured()).getAttribute("SameSite"));
    }
}
