package com.cauverystore.service;

import com.cauverystore.entities.GstRateMaster;
import com.cauverystore.entities.Product;
import com.cauverystore.repository.GstRateMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GstRateResolverTest {

    @Mock private GstRateMasterRepository rateRepo;

    @InjectMocks private GstRateResolver resolver;

    private Product product;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(resolver, "strictMode", false);
        ReflectionTestUtils.setField(resolver, "fallbackRate", 12.0);

        product = new Product();
        product.setName("Basmati Rice 5kg");
        product.setHsnCode("10063010");
    }

    private GstRateMaster rate(String hsn, double pct, LocalDate from, LocalDate to) {
        GstRateMaster r = new GstRateMaster();
        r.setHsnCode(hsn);
        r.setGstRate(pct);
        r.setEffectiveFrom(from);
        r.setEffectiveTo(to);
        r.setStatus(GstRateMaster.STATUS_VERIFIED);
        return r;
    }

    @Test
    void resolve_shouldSplitCgstSgst_forIntraStateSupply() {
        when(rateRepo.findApplicable(eq("10063010"), eq(GstRateMaster.STATUS_VERIFIED), any()))
                .thenReturn(List.of(rate("10063010", 5.0, LocalDate.of(2025, 9, 22), null)));

        GstRateResolver.Resolved r = resolver.resolve(product, false, LocalDate.of(2026, 8, 6));

        assertEquals(5.0, r.getTotalRate());
        assertEquals(2.5, r.getCgstRate());
        assertEquals(2.5, r.getSgstRate());
        assertEquals(0.0, r.getIgstRate());
        assertTrue(r.isFromMaster());
    }

    @Test
    void resolve_shouldApplyFullIgst_forInterStateSupply() {
        when(rateRepo.findApplicable(eq("10063010"), eq(GstRateMaster.STATUS_VERIFIED), any()))
                .thenReturn(List.of(rate("10063010", 18.0, LocalDate.of(2025, 9, 22), null)));

        GstRateResolver.Resolved r = resolver.resolve(product, true, LocalDate.of(2026, 8, 6));

        assertEquals(18.0, r.getIgstRate());
        assertEquals(0.0, r.getCgstRate());
        assertEquals(0.0, r.getSgstRate());
    }

    @Test
    void resolve_shouldFallBackToParentHeading_whenExactCodeHasNoRate() {
        when(rateRepo.findApplicable(eq("10063010"), anyString(), any())).thenReturn(List.of());
        when(rateRepo.findApplicable(eq("100630"), anyString(), any())).thenReturn(List.of());
        when(rateRepo.findApplicable(eq("1006"), eq(GstRateMaster.STATUS_VERIFIED), any()))
                .thenReturn(List.of(rate("1006", 5.0, LocalDate.of(2025, 9, 22), null)));

        GstRateResolver.Resolved r = resolver.resolve(product, false, LocalDate.of(2026, 8, 6));

        assertEquals(5.0, r.getTotalRate());
        assertTrue(r.isFromMaster());
    }

    @Test
    void resolve_shouldIgnoreUnverifiedRates() {
        // Only VERIFIED rows are ever queried, so an unverified proposal must not be charged.
        when(rateRepo.findApplicable(anyString(), eq(GstRateMaster.STATUS_VERIFIED), any()))
                .thenReturn(List.of());

        GstRateResolver.Resolved r = resolver.resolve(product, false, LocalDate.of(2026, 8, 6));

        assertFalse(r.isFromMaster(), "an unverified rate must not be treated as authoritative");
        assertEquals(12.0, r.getTotalRate(), "should have used the configured fallback");
    }

    @Test
    void resolve_shouldThrowInStrictMode_whenRateUnresolvable() {
        ReflectionTestUtils.setField(resolver, "strictMode", true);
        when(rateRepo.findApplicable(anyString(), anyString(), any())).thenReturn(List.of());

        assertThrows(GstRateResolver.GstRateUnresolvedException.class,
                () -> resolver.resolve(product, false, LocalDate.of(2026, 8, 6)));
    }

    @Test
    void resolve_shouldThrowInStrictMode_whenProductHasNoHsn() {
        ReflectionTestUtils.setField(resolver, "strictMode", true);
        product.setHsnCode(null);

        GstRateResolver.GstRateUnresolvedException ex =
                assertThrows(GstRateResolver.GstRateUnresolvedException.class,
                        () -> resolver.resolve(product, false, LocalDate.of(2026, 8, 6)));
        assertTrue(ex.getMessage().contains("no HSN code set"));
    }

    @Test
    void findRate_shouldReturnEmpty_whenHsnIsMissing() {
        assertTrue(resolver.findRate(null, LocalDate.now()).isEmpty());
        assertTrue(resolver.findRate("   ", LocalDate.now()).isEmpty());
    }

    @Test
    void resolve_shouldUseRateInForceOnTheSupplyDate_notToday() {
        // A pre-GST-2.0 order must still resolve to the rate that applied on its own date,
        // so a historical invoice can be reproduced exactly.
        LocalDate oldOrderDate = LocalDate.of(2025, 6, 1);
        when(rateRepo.findApplicable(eq("10063010"), eq(GstRateMaster.STATUS_VERIFIED), eq(oldOrderDate)))
                .thenReturn(List.of(rate("10063010", 12.0,
                        LocalDate.of(2017, 7, 1), LocalDate.of(2025, 9, 21))));

        GstRateResolver.Resolved r = resolver.resolve(product, false, oldOrderDate);

        assertEquals(12.0, r.getTotalRate());
        verify(rateRepo).findApplicable("10063010", GstRateMaster.STATUS_VERIFIED, oldOrderDate);
    }

    @Test
    void taxHelpers_shouldRoundToPaise() {
        when(rateRepo.findApplicable(eq("10063010"), eq(GstRateMaster.STATUS_VERIFIED), any()))
                .thenReturn(List.of(rate("10063010", 5.0, LocalDate.of(2025, 9, 22), null)));

        GstRateResolver.Resolved r = resolver.resolve(product, false, LocalDate.of(2026, 8, 6));

        // 399.75 @ 5%   = 19.9875  -> 19.99
        // 399.75 @ 2.5% =  9.99375 ->  9.99  (each half)
        assertEquals(19.99, r.taxOn(399.75));
        assertEquals(9.99, r.cgstOn(399.75));
        assertEquals(9.99, r.sgstOn(399.75));
    }

    @Test
    void totalTaxOnIntraState_shouldEqualCgstPlusSgst_notIndependentlyRoundedTotal() {
        // Rounding each half separately can differ from rounding the whole by a paisa
        // (here 9.99 + 9.99 = 19.98 vs taxOn = 19.99). An invoice must foot exactly, so
        // callers take totalTaxOn() rather than taxOn() for the intra-state total.
        when(rateRepo.findApplicable(eq("10063010"), eq(GstRateMaster.STATUS_VERIFIED), any()))
                .thenReturn(List.of(rate("10063010", 5.0, LocalDate.of(2025, 9, 22), null)));

        GstRateResolver.Resolved r = resolver.resolve(product, false, LocalDate.of(2026, 8, 6));

        assertEquals(19.98, r.totalTaxOn(399.75));
        assertEquals(r.cgstOn(399.75) + r.sgstOn(399.75), r.totalTaxOn(399.75), 0.0001);
    }

    @Test
    void totalTaxOnInterState_shouldEqualIgst() {
        when(rateRepo.findApplicable(eq("10063010"), eq(GstRateMaster.STATUS_VERIFIED), any()))
                .thenReturn(List.of(rate("10063010", 5.0, LocalDate.of(2025, 9, 22), null)));

        GstRateResolver.Resolved r = resolver.resolve(product, true, LocalDate.of(2026, 8, 6));

        assertEquals(r.igstOn(399.75), r.totalTaxOn(399.75), 0.0001);
    }
}
