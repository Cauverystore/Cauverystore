package com.cauverystore.erp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ErpNextSetupServiceTest {

    private final ErpNextClient erp = mock(ErpNextClient.class);
    private final ErpNextSetupService setup = new ErpNextSetupService(erp);

    @Test
    void createsWhatIsMissingAndLeavesTheRest() {
        ReflectionTestUtils.setField(setup, "allowNegativeStock", true);
        when(erp.exists(anyString(), anyString())).thenReturn(false);
        when(erp.exists("Item Group", ErpNextSetupService.PRODUCT_GROUP)).thenReturn(true);
        when(erp.get("Stock Settings", "Stock Settings")).thenReturn(new ObjectMapper().createObjectNode());

        List<Map<String, String>> report = setup.run();

        verify(erp).create(eq("Custom Field"), argThat(m -> "Sales Invoice".equals(m.get("dt"))
                && ErpNextSetupService.REF_FIELD.equals(m.get("fieldname")) && Integer.valueOf(1).equals(m.get("no_copy"))));
        verify(erp, never()).create(eq("Item Group"), argThat(m -> ErpNextSetupService.PRODUCT_GROUP.equals(m.get("item_group_name"))));
        verify(erp).create(eq("Item"), argThat(m -> ErpNextSetupService.CHARGES_ITEM.equals(m.get("item_code"))
                && Integer.valueOf(0).equals(m.get("is_stock_item"))));
        verify(erp).update("Stock Settings", "Stock Settings", Map.of("allow_negative_stock", 1));
        assertTrue(report.stream().noneMatch(r -> "failed".equals(r.get("result"))));
        assertTrue(setup.isDone());
    }

    @Test
    void aFailedStepIsReportedAndStopsTheSync() {
        when(erp.exists(anyString(), anyString())).thenReturn(true);
        when(erp.exists("Custom Field", "Item-" + ErpNextSetupService.REF_FIELD)).thenReturn(false);
        when(erp.create(eq("Custom Field"), anyMap())).thenThrow(new ErpNextClient.ErpException("not permitted"));

        assertThrows(ErpNextClient.ErpException.class, setup::ensure);
        assertFalse(setup.isDone());
    }

    @Test
    void runsOnlyOnceAfterSuccess() {
        when(erp.exists(anyString(), anyString())).thenReturn(true);
        setup.ensure();
        setup.ensure();
        verify(erp, times(1)).exists("Custom Field", "Sales Invoice-" + ErpNextSetupService.REF_FIELD);
    }

    @Test
    void erpLinksPointAtTheRightPages() {
        assertEquals("https://erp.example.in/app/sales-invoice/ACC-SINV-2026-00001",
                AdminErpNextController.erpLink("https://erp.example.in", ErpSyncRecord.SALES_INVOICE, "ACC-SINV-2026-00001"));
        assertEquals("https://erp.example.in/app/item/CS-P7",
                AdminErpNextController.erpLink("https://erp.example.in", ErpSyncRecord.ITEM, "CS-P7"));
        assertNull(AdminErpNextController.erpLink("", ErpSyncRecord.ITEM, "CS-P7"));
    }
}
