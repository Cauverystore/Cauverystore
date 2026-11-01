package com.cauverystore.service;

import com.cauverystore.client.GstnClient;
import com.cauverystore.entities.*;
import com.cauverystore.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The e-invoice retry queue used to relabel rows without calling the IRP, so an invoice whose
 * IRN failed at checkout stayed unregistered for good. These pin what each row now does.
 */
@ExtendWith(MockitoExtension.class)
class EinvoiceQueueTest {

    @Mock private GstInvoiceRepository invoiceRepo;
    @Mock private GstInvoiceItemRepository itemRepo;
    @Mock private GstSyncQueueRepository syncRepo;
    @Mock private GstConfigurationRepository configRepo;
    @Mock private SellerRegistrationRepository sellerRegRepo;
    @Mock private OrderRepository orderRepo;
    @Mock private ProductRepository productRepo;
    @Mock private UserRepository userRepo;
    @Mock private AuditService auditService;
    @Mock private GstnClient gstnClient;
    @Mock private TcsRecordRepository tcsRepo;
    @Mock private GstRateResolver gstRateResolver;
    @Mock private StateMasterRepository stateRepo;
    @Mock private PincodeStateRangeRepository pincodeRepo;
    @Mock private CreditNoteRepository creditNoteRepo;
    @Mock private DebitNoteRepository debitNoteRepo;

    @InjectMocks
    private GstInvoiceService service;

    private GstInvoice inv;

    @BeforeEach
    void setUp() {
        inv = new GstInvoice();
        inv.setId(1L);
        inv.setSellerId(7L);
        inv.setInvoiceNumber("CS/1");
        inv.setInvoiceDate(LocalDate.now());
        inv.setBuyerGstin("33AAACR5055K1Z5");
        inv.setEinvoicingRequired(true);
        lenient().when(invoiceRepo.findById(1L)).thenReturn(Optional.of(inv));
        lenient().when(syncRepo.findByInvoiceIdAndSyncType(any(), any())).thenReturn(List.of());
    }

    private GstSyncQueue row(String type, Long id) {
        GstSyncQueue q = new GstSyncQueue();
        q.setId(100L);
        q.setInvoiceId(id);
        q.setSyncType(type);
        q.setStatus("PENDING");
        when(syncRepo.findByStatusInOrderByCreatedAtAsc(any())).thenReturn(List.of(q));
        return q;
    }

    @Test
    void aQueuedInvoiceIsActuallySentToTheIrp() {
        GstSyncQueue q = row(GstInvoiceService.SYNC_INVOICE, 1L);
        when(gstnClient.generateIrn(inv)).thenReturn(Map.of("irn", "a".repeat(64), "ackNo", "1", "ackDate", "2026-11-01", "qrCode", "QR"));

        assertEquals(1, service.processSyncQueue(10));

        verify(gstnClient).generateIrn(inv);
        assertEquals("a".repeat(64), inv.getIrn());
        assertEquals("COMPLETED", q.getStatus());
    }

    @Test
    void aConsumerInvoiceIsSkippedWithoutCallingTheIrp() {
        inv.setEinvoicingRequired(false);
        GstSyncQueue q = row(GstInvoiceService.SYNC_INVOICE, 1L);

        assertEquals(0, service.processSyncQueue(10));

        verifyNoInteractions(gstnClient);
        assertEquals("SKIPPED", q.getStatus());
    }

    @Test
    void aPortalOutageIsRetriedLater_notGivenUpOn() {
        GstSyncQueue q = row(GstInvoiceService.SYNC_INVOICE, 1L);
        when(gstnClient.generateIrn(inv)).thenThrow(new RuntimeException("Could not register invoice CS/1 with the IRP: timeout"));

        service.processSyncQueue(10);

        assertEquals("PENDING", q.getStatus());
        assertEquals(1, q.getRetryCount());
        assertNull(inv.getIrn(), "never a made-up IRN");
    }

    @Test
    void aRetryWaitsItsTurn() {
        GstSyncQueue q = row(GstInvoiceService.SYNC_INVOICE, 1L);
        q.setRetryCount(3);
        q.setLastAttemptAt(LocalDateTime.now().minusMinutes(5));   // needs 20

        assertEquals(0, service.processSyncQueue(10));
        verifyNoInteractions(gstnClient);
        assertEquals(5, GstInvoiceService.retryDelayMinutes(1));
        assertEquals(20, GstInvoiceService.retryDelayMinutes(3));
        assertEquals(24 * 60, GstInvoiceService.retryDelayMinutes(30));
    }

    @Test
    void aRefusalThatNeedsTheDataFixedIsNotRetried() {
        GstSyncQueue q = row(GstInvoiceService.SYNC_INVOICE, 1L);
        when(gstnClient.generateIrn(inv)).thenThrow(new RuntimeException("Invoice CS/1 cannot be e-invoiced yet: the buyer's address has no 6-digit PIN code"));

        service.processSyncQueue(10);

        assertEquals("FAILED", q.getStatus());
    }

    @Test
    void pastThirtyDaysATenCroreTaxpayerGetsAClearFailure_notEndlessRetries() {
        inv.setInvoiceDate(LocalDate.now().minusDays(31));
        GstConfiguration cfg = new GstConfiguration();
        cfg.setAnnualTurnover(60_00_00_000.0);
        when(configRepo.findBySellerId(7L)).thenReturn(Optional.of(cfg));
        GstSyncQueue q = row(GstInvoiceService.SYNC_INVOICE, 1L);

        service.processSyncQueue(10);

        verifyNoInteractions(gstnClient);
        assertEquals("FAILED", q.getStatus());
        assertEquals("IRN_TOO_LATE", inv.getStatus());
        assertTrue(q.getErrorMessage().contains("30 days"));
    }

    @Test
    void thirtyDaysDoesNotApplyBelowTenCrore() {
        GstConfiguration cfg = new GstConfiguration();
        cfg.setAnnualTurnover(6_00_00_000.0);
        when(configRepo.findBySellerId(7L)).thenReturn(Optional.of(cfg));
        assertNull(service.tooLateForIrp(LocalDate.of(2026, 1, 1), 7L, LocalDate.of(2026, 11, 1)));
    }

    @Test
    void aCreditNoteAgainstAnEinvoiceIsRegisteredAsCrn_namingTheInvoice() {
        inv.setInvoiceDate(LocalDate.of(2026, 10, 20));
        CreditNote cn = new CreditNote();
        cn.setId(9L);
        cn.setInvoiceId(1L);
        cn.setCreditNoteNumber("CN/1");
        cn.setCreditNoteDate(LocalDate.now());
        cn.setTaxableAmount(100.0);
        cn.setCgstAmount(2.5);
        cn.setSgstAmount(2.5);
        cn.setIgstAmount(0.0);
        cn.setTotalAmount(105.0);
        CreditNoteItem item = new CreditNoteItem();
        item.setHsnCode("1006");
        item.setTaxableValue(100.0);
        cn.setItems(new ArrayList<>(List.of(item)));
        when(creditNoteRepo.findById(9L)).thenReturn(Optional.of(cn));
        GstSyncQueue q = row(GstInvoiceService.SYNC_CREDIT_NOTE, 9L);
        when(gstnClient.generateIrn(any())).thenAnswer(a -> {
            GstInvoice sent = a.getArgument(0);
            assertEquals("CRN", sent.getIrpDocType());
            assertEquals("CN/1", sent.getInvoiceNumber());
            assertEquals("CS/1", sent.getPrecedingInvoiceNumber());
            assertEquals("33AAACR5055K1Z5", sent.getBuyerGstin());
            assertEquals(1, sent.getItems().size());
            return Map.of("irn", "b".repeat(64), "ackNo", "2", "ackDate", "2026-11-01", "qrCode", "QR");
        });

        service.processSyncQueue(10);

        assertEquals("b".repeat(64), cn.getIrn());
        assertEquals("REGISTERED", cn.getEinvoiceStatus());
        assertEquals("COMPLETED", q.getStatus());
    }

    @Test
    void aCreditNoteOnAConsumerInvoiceNeedsNoIrn() {
        inv.setEinvoicingRequired(false);
        CreditNote cn = new CreditNote();
        cn.setId(9L);
        cn.setInvoiceId(1L);
        when(creditNoteRepo.findById(9L)).thenReturn(Optional.of(cn));
        GstSyncQueue q = row(GstInvoiceService.SYNC_CREDIT_NOTE, 9L);

        service.processSyncQueue(10);

        verifyNoInteractions(gstnClient);
        assertEquals("NOT_REQUIRED", cn.getEinvoiceStatus());
        assertEquals("SKIPPED", q.getStatus());
    }
}
