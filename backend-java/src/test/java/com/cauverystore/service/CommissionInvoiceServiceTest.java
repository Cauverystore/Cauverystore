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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Covers the marketplace's own invoice to its sellers.
 *
 * The thing most likely to be got backwards is the place of supply: this invoice's follows the
 * SELLER, because the marketplace is supplying them - not the consumer the goods went to.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommissionInvoiceServiceTest {

    @Mock private CommissionInvoiceRepository invoiceRepo;
    @Mock private CommissionRateRepository rateRepo;
    @Mock private GstConfigurationRepository configRepo;
    @Mock private SellerRegistrationRepository sellerRegRepo;
    @Mock private OrderRepository orderRepo;
    @Mock private GstInvoiceRepository gstInvoiceRepo;
    @Mock private AuditService auditService;

    private CommissionInvoiceService service;
    private GstConfiguration config;
    private SellerRegistration seller;

    @BeforeEach
    void setUp() {
        service = new CommissionInvoiceService(invoiceRepo, rateRepo, configRepo,
                sellerRegRepo, orderRepo, gstInvoiceRepo, auditService);

        config = new GstConfiguration();
        config.setGstin("33AABCC1234D1Z5");
        config.setLegalName("Cauvery Retail Private Limited");
        config.setStateCode("33");
        config.setCommissionSacCode("998599");
        config.setCommissionGstRate(18.0);

        seller = new SellerRegistration();
        seller.setBusinessName("Ananya Traders");
        seller.setGstin("33AABCA9012K1ZB");
        seller.setGstinStateCode("33");

        when(configRepo.findAll()).thenReturn(List.of(config));
        when(sellerRegRepo.findByUserId(12L)).thenReturn(Optional.of(seller));
        when(invoiceRepo.findBySellerIdAndPeriod(anyLong(), any())).thenReturn(Optional.empty());
        when(invoiceRepo.findTopByInvoiceNumberStartingWithOrderByInvoiceNumberDesc(any()))
                .thenReturn(Optional.empty());
        when(invoiceRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(gstInvoiceRepo.findByOrderId(anyLong())).thenReturn(Optional.empty());
    }

    private CommissionRate rate(Long sellerId, Long categoryId, double percent) {
        CommissionRate r = new CommissionRate();
        r.setSellerId(sellerId);
        r.setCategoryId(categoryId);
        r.setRatePercent(percent);
        r.setFixedFee(0.0);
        r.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        return r;
    }

    private Order order(long id, double total, String status) {
        Order o = new Order();
        o.setId(id);
        o.setSellerId(12L);
        o.setTotalAmount(total);
        o.setStatus(status);
        o.setCreatedAt(LocalDateTime.of(2026, 8, 12, 10, 0));
        o.setItems(List.of());
        return o;
    }

    // ------------------------------------------------------------ rate card

    @Test
    void resolveRate_shouldPreferTheSellerSpecificRateOverTheCategoryAndDefault() {
        when(rateRepo.findBySellerIdIsNullOrSellerId(12L)).thenReturn(List.of(
                rate(null, null, 8.0),        // platform default
                rate(null, 3L, 6.0),          // category
                rate(12L, 3L, 4.0)));         // this seller in this category

        assertEquals(4.0, service.resolveRate(12L, 3L, LocalDate.of(2026, 8, 12))
                .orElseThrow().getRatePercent());
    }

    @Test
    void resolveRate_shouldFallBackToTheCategoryThenTheDefault() {
        when(rateRepo.findBySellerIdIsNullOrSellerId(12L)).thenReturn(List.of(
                rate(null, null, 8.0),
                rate(null, 3L, 6.0)));

        assertEquals(6.0, service.resolveRate(12L, 3L, LocalDate.of(2026, 8, 12))
                .orElseThrow().getRatePercent());
        assertEquals(8.0, service.resolveRate(12L, 99L, LocalDate.of(2026, 8, 12))
                .orElseThrow().getRatePercent(), "no rate for category 99, so the default applies");
    }

    @Test
    void resolveRate_shouldIgnoreARateThatIsNotYetInForceOrHasEnded() {
        CommissionRate future = rate(null, null, 12.0);
        future.setEffectiveFrom(LocalDate.of(2026, 12, 1));
        CommissionRate ended = rate(null, null, 5.0);
        ended.setEffectiveTo(LocalDate.of(2026, 3, 31));
        when(rateRepo.findBySellerIdIsNullOrSellerId(12L)).thenReturn(List.of(future, ended));

        assertTrue(service.resolveRate(12L, null, LocalDate.of(2026, 8, 12)).isEmpty());
    }

    @Test
    void resolveRate_shouldReturnEmptyRatherThanZero_whenNothingApplies() {
        // A silent zero would issue a nil invoice and quietly forgo the fee.
        when(rateRepo.findBySellerIdIsNullOrSellerId(12L)).thenReturn(List.of());

        assertTrue(service.resolveRate(12L, null, LocalDate.of(2026, 8, 12)).isEmpty());
    }

    // ------------------------------------------------------------- invoicing

    @Test
    void shouldChargeCgstAndSgst_whenTheSellerIsInTheMarketplacesOwnState() {
        // Place of supply follows the SELLER here, not the consumer.
        when(rateRepo.findBySellerIdIsNullOrSellerId(12L)).thenReturn(List.of(rate(null, null, 10.0)));
        when(orderRepo.findAll()).thenReturn(List.of(order(1L, 1000.0, "DELIVERED")));

        CommissionInvoice inv = service.generateForSeller(12L, "082026", "admin@cauverystore.in");

        assertFalse(inv.getIsInterState());
        assertEquals(100.0, inv.getTaxableAmount(), 0.01);
        assertEquals(9.0, inv.getCgstAmount(), 0.01);
        assertEquals(9.0, inv.getSgstAmount(), 0.01);
        assertEquals(0.0, inv.getIgstAmount(), 0.01);
        assertEquals(118.0, inv.getTotalAmount(), 0.01);
        assertEquals("998599", inv.getSacCode());
    }

    @Test
    void shouldChargeIgst_whenTheSellerIsInAnotherState() {
        seller.setGstinStateCode("29");
        when(rateRepo.findBySellerIdIsNullOrSellerId(12L)).thenReturn(List.of(rate(null, null, 10.0)));
        when(orderRepo.findAll()).thenReturn(List.of(order(1L, 1000.0, "DELIVERED")));

        CommissionInvoice inv = service.generateForSeller(12L, "082026", "admin@cauverystore.in");

        assertTrue(inv.getIsInterState());
        assertEquals("29", inv.getSellerStateCode(), "place of supply is the seller's state");
        assertEquals(18.0, inv.getIgstAmount(), 0.01);
        assertEquals(0.0, inv.getCgstAmount(), 0.01);
    }

    @Test
    void shouldNotBillCancelledOrReturnedOrders() {
        // Commission follows a sale that stood; charging on a returned order takes a fee for a
        // supply that did not happen.
        when(rateRepo.findBySellerIdIsNullOrSellerId(12L)).thenReturn(List.of(rate(null, null, 10.0)));
        when(orderRepo.findAll()).thenReturn(List.of(
                order(1L, 1000.0, "DELIVERED"),
                order(2L, 500.0, "CANCELLED"),
                order(3L, 700.0, "RETURNED")));

        CommissionInvoice inv = service.generateForSeller(12L, "082026", "admin@cauverystore.in");

        assertEquals(1, inv.getLines().size());
        assertEquals(100.0, inv.getTaxableAmount(), 0.01);
    }

    @Test
    void shouldOnlyBillOrdersInsideThePeriod() {
        when(rateRepo.findBySellerIdIsNullOrSellerId(12L)).thenReturn(List.of(rate(null, null, 10.0)));
        Order july = order(2L, 900.0, "DELIVERED");
        july.setCreatedAt(LocalDateTime.of(2026, 7, 30, 9, 0));
        when(orderRepo.findAll()).thenReturn(List.of(order(1L, 1000.0, "DELIVERED"), july));

        CommissionInvoice inv = service.generateForSeller(12L, "082026", "admin@cauverystore.in");

        assertEquals(1, inv.getLines().size());
        assertEquals(1L, inv.getLines().get(0).getOrderId());
    }

    @Test
    void shouldNotBillTheSamePeriodTwice() {
        // Month-end jobs get re-run, and a seller must not be charged twice for it.
        CommissionInvoice already = new CommissionInvoice();
        already.setInvoiceNumber("CM2026080001");
        when(invoiceRepo.findBySellerIdAndPeriod(12L, "082026")).thenReturn(Optional.of(already));

        CommissionInvoice inv = service.generateForSeller(12L, "082026", "admin@cauverystore.in");

        assertEquals("CM2026080001", inv.getInvoiceNumber());
        verify(invoiceRepo, never()).save(any());
    }

    @Test
    void shouldLeaveAnOrderUnbilledRatherThanChargeZero_whenNoRateApplies() {
        when(rateRepo.findBySellerIdIsNullOrSellerId(12L)).thenReturn(List.of());
        when(orderRepo.findAll()).thenReturn(List.of(order(1L, 1000.0, "DELIVERED")));

        CommissionInvoiceService.CommissionException ex = assertThrows(
                CommissionInvoiceService.CommissionException.class,
                () -> service.generateForSeller(12L, "082026", "admin@cauverystore.in"));
        assertTrue(ex.getMessage().contains("nothing to invoice"));
    }

    @Test
    void shouldRefuseWhenTheSellerHasNoState() {
        // Without a state there is no place of supply, so the invoice cannot state its tax.
        seller.setGstinStateCode(null);
        seller.setGstin(null);
        when(rateRepo.findBySellerIdIsNullOrSellerId(12L)).thenReturn(List.of(rate(null, null, 10.0)));
        when(orderRepo.findAll()).thenReturn(List.of(order(1L, 1000.0, "DELIVERED")));

        CommissionInvoiceService.CommissionException ex = assertThrows(
                CommissionInvoiceService.CommissionException.class,
                () -> service.generateForSeller(12L, "082026", "admin@cauverystore.in"));
        assertTrue(ex.getMessage().toLowerCase().contains("place of supply"));
    }

    @Test
    void shouldKeepTheInvoiceNumberWithinRule46sLimit() {
        when(rateRepo.findBySellerIdIsNullOrSellerId(12L)).thenReturn(List.of(rate(null, null, 10.0)));
        when(orderRepo.findAll()).thenReturn(List.of(order(1L, 1000.0, "DELIVERED")));

        String number = service.generateForSeller(12L, "082026", null).getInvoiceNumber();

        assertTrue(number.length() <= 16, "Rule 46 caps an invoice number at 16 characters");
        assertTrue(number.startsWith("CM202608"));
    }

    @Test
    void shouldRecordTheRateOnTheLine_soARateChangeCannotRestateWhatWasBilled() {
        when(rateRepo.findBySellerIdIsNullOrSellerId(12L)).thenReturn(List.of(rate(null, null, 7.5)));
        when(orderRepo.findAll()).thenReturn(List.of(order(1L, 200.0, "DELIVERED")));

        CommissionInvoiceLine line = service
                .generateForSeller(12L, "082026", null).getLines().get(0);

        assertEquals(7.5, line.getRatePercent());
        assertEquals(200.0, line.getOrderValue());
        assertEquals(15.0, line.getCommissionAmount(), 0.01);
    }
}
