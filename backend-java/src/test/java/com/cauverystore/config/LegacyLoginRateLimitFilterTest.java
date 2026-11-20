package com.cauverystore.config;

import com.cauverystore.service.RateLimiterService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class LegacyLoginRateLimitFilterTest {

    private MockHttpServletResponse post(LegacyLoginRateLimitFilter filter, String path, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
        req.setRequestURI(path);
        req.addHeader("X-Forwarded-For", ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    void limitsTheOlderLoginAddressesPerVisitor() throws Exception {
        LegacyLoginRateLimitFilter filter = new LegacyLoginRateLimitFilter(new RateLimiterService());
        ReflectionTestUtils.setField(filter, "loginIpMax", 3);

        for (int i = 0; i < 3; i++) {
            assertEquals(200, post(filter, "/api/admin/login", "203.0.113.1").getStatus());
        }
        // Switching to another old address does not give more tries.
        assertEquals(429, post(filter, "/api/seller/login", "203.0.113.1").getStatus());
        // Someone else is not affected.
        assertEquals(200, post(filter, "/api/admin/login", "203.0.113.2").getStatus());
        // Other pages are never limited here.
        for (int i = 0; i < 5; i++) {
            assertEquals(200, post(filter, "/api/products", "203.0.113.1").getStatus());
        }
    }
}
