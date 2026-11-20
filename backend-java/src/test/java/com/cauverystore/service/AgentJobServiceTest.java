package com.cauverystore.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentJobServiceTest {

    @Test
    void priceIsCostTimesTwoRoundedUpToEndInNine() {
        assertEquals(309, AgentJobService.priceRule(151.4));   // 302.8 -> 303 -> 309
        assertEquals(199, AgentJobService.priceRule(99.5));    // 199 already ends in 9
        assertEquals(209, AgentJobService.priceRule(100));     // 200 -> 209
        assertEquals(59, AgentJobService.priceRule(25.25));    // 50.5 -> 51 -> 59
    }

    @Test
    void onlySupplierProductLinksAreAccepted() {
        assertEquals("https://deodap.in/products/steel-bottle",
                AgentJobService.normaliseLink("https://www.deodap.in/products/steel-bottle?variant=1"));
        assertEquals("https://baapstore.com/products/silk-saree",
                AgentJobService.normaliseLink("https://baapstore.com/collections/sarees/products/silk-saree"));
        assertNull(AgentJobService.normaliseLink("http://deodap.in/products/x"));          // not https
        assertNull(AgentJobService.normaliseLink("https://evil.example/products/x"));      // other site
        assertNull(AgentJobService.normaliseLink("https://deodap.in/collections/kitchen")); // not a product
        assertNull(AgentJobService.normaliseLink("not a link"));
    }
}
