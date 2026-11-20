package com.cauverystore.service;

import com.cauverystore.entities.ProductCompliance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComplianceDefaultsTest {

    private ProductComplianceService service() {
        ProductComplianceService s = new ProductComplianceService(null, null, null, null, null, null, null);
        s.defaultCareEmail = "support@cauverystore.in";
        s.defaultCarePhone = "";
        s.defaultMinOrderQty = 1;
        s.defaultMaxOrderQty = 10;
        return s;
    }

    @Test
    void fillsOnlyBlanks() {
        ProductCompliance c = new ProductCompliance();
        assertTrue(service().fillDefaults(c));
        assertEquals("support@cauverystore.in", c.getCustomerCareEmail());
        assertNull(c.getCustomerCarePhone(), "no phone set, so it stays blank");
        assertEquals(1, c.getMinOrderQty());
        assertEquals(10, c.getMaxOrderQty());
    }

    @Test
    void keepsWhatTheProductAlreadyHas() {
        ProductCompliance c = new ProductCompliance();
        c.setCustomerCareEmail("seller@example.com");
        c.setMinOrderQty(2);
        c.setMaxOrderQty(4);
        service().fillDefaults(c);
        assertEquals("seller@example.com", c.getCustomerCareEmail());
        assertEquals(2, c.getMinOrderQty());
        assertEquals(4, c.getMaxOrderQty());
    }

    @Test
    void neverSetsAMaximumBelowTheMinimum() {
        ProductCompliance c = new ProductCompliance();
        c.setMinOrderQty(20);
        service().fillDefaults(c);
        assertNull(c.getMaxOrderQty());
    }
}
