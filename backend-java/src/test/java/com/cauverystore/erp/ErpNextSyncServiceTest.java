package com.cauverystore.erp;

import com.cauverystore.entities.CommissionInvoice;
import com.cauverystore.entities.SellerPayout;
import com.cauverystore.repository.CommissionInvoiceRepository;
import com.cauverystore.repository.SellerPayoutRepository;
import com.cauverystore.repository.SellerRegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The store must not bill commission to, or pay out to, its own seller account in the books. */
class ErpNextSyncServiceTest {

    private static final long OWN_SELLER = 46L, OTHER_SELLER = 50L;

    private final ErpNextClient erp = mock(ErpNextClient.class);
    private final ErpSyncRecordRepository records = mock(ErpSyncRecordRepository.class);
    private final CommissionInvoiceRepository invoices = mock(CommissionInvoiceRepository.class);
    private final SellerPayoutRepository payouts = mock(SellerPayoutRepository.class);
    private final SellerRegistrationRepository registrations = mock(SellerRegistrationRepository.class);
    private final ErpNextStoreSyncService storeSync = mock(ErpNextStoreSyncService.class);
    private ErpNextSyncService service;

    @BeforeEach
    void setUp() {
        service = new ErpNextSyncService(erp, records, invoices, payouts, registrations, storeSync);
        ReflectionTestUtils.setField(service, "company", "Cauvery Store");
        ReflectionTestUtils.setField(service, "abbr", "CS");
        when(erp.isConfigured()).thenReturn(true);
        when(storeSync.syncAll(anyBoolean())).thenReturn(Map.of());
        when(storeSync.ownSellerIds()).thenReturn(Set.of(OWN_SELLER));
        when(records.findByEntityTypeAndEntityId(anyString(), anyLong())).thenReturn(Optional.empty());
        when(invoices.findAll()).thenReturn(List.of());
        when(payouts.findAll()).thenReturn(List.of());
    }

    private static CommissionInvoice commission(long id, long sellerId) {
        CommissionInvoice inv = new CommissionInvoice();
        ReflectionTestUtils.setField(inv, "id", id);
        inv.setSellerId(sellerId);
        inv.setInvoiceNumber("COM-" + id);
        return inv;
    }

    private static SellerPayout payout(long id, long sellerId) {
        SellerPayout p = new SellerPayout();
        ReflectionTestUtils.setField(p, "id", id);
        p.setSellerId(sellerId);
        return p;
    }

    @Test
    void commissionForTheStoresOwnSellerIsNotSent() {
        when(invoices.findAll()).thenReturn(List.of(commission(1L, OWN_SELLER)));
        service.syncAll(false);
        verify(erp, never()).findNameByField(anyString(), anyString(), anyString());
        verify(records, never()).save(any());
    }

    @Test
    void payoutToTheStoresOwnSellerIsNotSent() {
        when(payouts.findAll()).thenReturn(List.of(payout(2L, OWN_SELLER)));
        service.syncAll(false);
        verify(records, never()).save(any());
    }

    @Test
    void commissionForOtherSellersIsStillSent() {
        when(invoices.findAll()).thenReturn(List.of(commission(3L, OTHER_SELLER)));
        service.syncAll(false);
        verify(erp).findNameByField("Sales Invoice", "po_no", "COM-3");
    }

    @Test
    void payoutToOtherSellersIsStillAttempted() {
        when(payouts.findAll()).thenReturn(List.of(payout(4L, OTHER_SELLER)));
        service.syncAll(false);
        verify(records).save(any());
    }
}
