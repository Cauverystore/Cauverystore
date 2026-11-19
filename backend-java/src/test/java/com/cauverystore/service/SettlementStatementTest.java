package com.cauverystore.service;

import com.cauverystore.entities.*;
import com.cauverystore.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * The statement a seller is paid from. The earlier preview left out the GST on the
 * marketplace's fees and TCS, and so overstated every payout by both.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettlementStatementTest {

    @Mock private CommissionInvoiceRepository invoiceRepo;
    @Mock private CommissionRateRepository rateRepo;
    @Mock private GstConfigurationRepository configRepo;
    @Mock private SellerRegistrationRepository sellerRegRepo;
    @Mock private OrderRepository orderRepo;
    @Mock private GstInvoiceRepository gstInvoiceRepo;
    @Mock private AuditService auditService;
    @Mock private TcsRecordRepository tcsRepo;
    @Mock private SellerPayoutRepository payoutRepo;

    private CommissionInvoiceService service;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);

    @BeforeEach
    void setUp() {
        service = new CommissionInvoiceService(invoiceRepo, rateRepo, configRepo,
                sellerRegRepo, orderRepo, gstInvoiceRepo, auditService);
        service.setSettlementSources(tcsRepo, payoutRepo);

        GstConfiguration config = new GstConfiguration();
        config.setCommissionGstRate(18.0);
        when(configRepo.findAll()).thenReturn(List.of(config));
        when(gstInvoiceRepo.findByOrderId(anyLong())).thenReturn(Optional.empty());

        CommissionRate r = new CommissionRate();
        r.setRatePercent(10.0);
        r.setFixedFee(10.0);
        r.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        when(rateRepo.findBySellerIdIsNullOrSellerId(12L)).thenReturn(List.of(r));

        Order delivered = order(1L, 10_000.0, "DELIVERED");
        delivered.setDeliveredAt(LocalDateTime.of(2026, 8, 14, 12, 0));
        Order returned = order(2L, 4_000.0, "RETURNED");
        when(orderRepo.findAll()).thenReturn(List.of(delivered, returned));
        when(orderRepo.findById(1L)).thenReturn(Optional.of(delivered));
        when(orderRepo.findById(2L)).thenReturn(Optional.of(returned));

        when(tcsRepo.findByOrderId(1L)).thenReturn(List.of(tcs(50.0, TcsRecord.ENTRY_COLLECTION)));
        when(payoutRepo.findBySellerIdOrderByPaidOnDescIdDesc(12L)).thenReturn(List.of(
                payout(5_000.0, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), false),
                payout(9_999.0, LocalDate.of(2026, 9, 2), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), true),
                payout(700.0, LocalDate.of(2026, 8, 5), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), false)));
    }

    private static Order order(long id, double total, String status) {
        Order o = new Order();
        o.setId(id);
        o.setSellerId(12L);
        o.setTotalAmount(total);
        o.setStatus(status);
        o.setCreatedAt(LocalDateTime.of(2026, 8, 12, 10, 0));
        o.setItems(List.of());
        return o;
    }

    private static TcsRecord tcs(double amount, String type) {
        TcsRecord t = new TcsRecord();
        t.setTcsAmount(amount);
        t.setEntryType(type);
        return t;
    }

    private static SellerPayout payout(double amount, LocalDate paidOn, LocalDate from, LocalDate to, boolean voided) {
        SellerPayout p = new SellerPayout();
        p.setAmount(amount);
        p.setPaidOn(paidOn);
        p.setPeriodFrom(from);
        p.setPeriodTo(to);
        p.setVoided(voided);
        return p;
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyDeductionIsOnTheLine_andTheyAddUp() {
        Map<String, Object> s = service.settlementStatement(12L, "082026", TODAY);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) s.get("orders");

        assertEquals(1, rows.size(), "the returned order pays nothing and carries no commission");
        Map<String, Object> r = rows.get(0);
        assertEquals(10_000.0, (double) r.get("orderValue"), 0.001);
        assertEquals(1_000.0, (double) r.get("commission"), 0.001);
        assertEquals(10.0, (double) r.get("handlingFee"), 0.001);
        assertEquals(181.80, (double) r.get("gstOnFees"), 0.001, "18% on commission + handling");
        assertEquals(236.00, (double) r.get("gatewayFee"), 0.001);
        assertEquals(25.84, (double) r.get("transferFee"), 0.001);
        assertEquals(50.0, (double) r.get("tcs"), 0.001);
        assertEquals(8_496.36, (double) r.get("payable"), 0.001);
        assertEquals(LocalDate.of(2026, 8, 22), r.get("payableFrom"));
        assertEquals(true, r.get("due"));
    }

    @Test
    void payoutsForTheMonthAreSetAgainstWhatIsDue() {
        Map<String, Object> s = service.settlementStatement(12L, "082026", TODAY);

        assertEquals(8_496.36, (double) s.get("payableNow"), 0.001);
        assertEquals(5_000.0, (double) s.get("totalPaid"), 0.001, "voided and July payouts left out");
        assertEquals(3_496.36, (double) s.get("balanceDue"), 0.001);
    }

    @Test
    void notYetPayableUntilEightDaysAfterDelivery() {
        Map<String, Object> s = service.settlementStatement(12L, "082026", LocalDate.of(2026, 8, 21));
        assertEquals(0.0, (double) s.get("payableNow"), 0.001);
        assertEquals(8_496.36, (double) s.get("notYetPayable"), 0.001);
    }

    @Test
    void aReversedTcsCountsAsNone() {
        when(tcsRepo.findByOrderId(1L)).thenReturn(List.of(
                tcs(50.0, TcsRecord.ENTRY_COLLECTION), tcs(50.0, TcsRecord.ENTRY_REVERSAL)));
        Map<String, Object> s = service.settlementStatement(12L, "082026", TODAY);
        assertEquals(0.0, (double) s.get("totalTcs"), 0.001);
    }

    @Test
    void aPayoutWithNoPeriodBelongsToTheMonthItWasPaid() {
        SellerPayout p = payout(1.0, LocalDate.of(2026, 8, 20), null, null, false);
        assertTrue(CommissionInvoiceService.coversMonth(p, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));
        assertFalse(CommissionInvoiceService.coversMonth(p, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
    }
}
