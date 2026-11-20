package com.cauverystore.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpTest {

    @Test
    void usesTheAddressTheProxyAddedNotOneTheVisitorTypedIn() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        // A visitor can send their own X-Forwarded-For; the proxy appends the real address last.
        req.addHeader("X-Forwarded-For", "1.2.3.4, 203.0.113.9");
        assertEquals("203.0.113.9", ClientIp.of(req));
    }

    @Test
    void fallsBackToXRealIpThenTheConnection() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        req.addHeader("X-Real-IP", "198.51.100.7");
        assertEquals("198.51.100.7", ClientIp.of(req));

        MockHttpServletRequest bare = new MockHttpServletRequest();
        bare.setRemoteAddr("10.0.0.1");
        assertEquals("10.0.0.1", ClientIp.of(bare));
    }

    @Test
    void ignoresBlankAndUnknownEntries() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        req.addHeader("X-Forwarded-For", "203.0.113.9, unknown , ");
        assertEquals("203.0.113.9", ClientIp.of(req));
    }
}
