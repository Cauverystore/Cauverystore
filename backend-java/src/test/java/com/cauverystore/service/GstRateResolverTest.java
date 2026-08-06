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
import java.util.Optional;

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

    private GstRateMaster conditional(String hsn, double pct, String type, double threshold) {
        GstRateMaster r = rate(hsn, pct, LocalDate.of(2025, 9, 22), null);
        r.setConditionType(type);
        r.setThresholdAmount(threshold);
        r.setThresholdUnit("piece");
        return r;
    }

    @Test
    void resolve_shouldPickLowerBand_whenApparelPriceIsAtOrBelowThreshold() {
        product.setHsnCode("61");
        when(rateRepo.findApplicable(eq("61"), eq(GstRateMaster.STATUS_VERIFIED), any()))
                .thenReturn(List.of(
                        conditional("61", 5.0, GstRateMaster.CONDITION_VALUE_UPTO, 2500.0),
                        conditional("61", 18.0, GstRateMaster.CONDITION_VALUE_ABOVE, 2500.0)));

        assertEquals(5.0, resolver.resolve(product, false, LocalDate.of(2026, 8, 6), 1499.0).getTotalRate());
        // exactly on the threshold belongs to the lower band ("not exceeding Rs 2500")
        assertEquals(5.0, resolver.resolve(product, false, LocalDate.of(2026, 8, 6), 2500.0).getTotalRate());
    }

    @Test
    void resolve_shouldPickUpperBand_whenApparelPriceIsAboveThreshold() {
        product.setHsnCode("61");
        when(rateRepo.findApplicable(eq("61"), eq(GstRateMaster.STATUS_VERIFIED), any()))
                .thenReturn(List.of(
                        conditional("61", 5.0, GstRateMaster.CONDITION_VALUE_UPTO, 2500.0),
                        conditional("61", 18.0, GstRateMaster.CONDITION_VALUE_ABOVE, 2500.0)));

        assertEquals(18.0, resolver.resolve(product, false, LocalDate.of(2026, 8, 6), 2500.01).getTotalRate());
        assertEquals(18.0, resolver.resolve(product, false, LocalDate.of(2026, 8, 6), 3999.0).getTotalRate());
    }

    @Test
    void resolve_shouldMatchChapterLevelCode_forApparelListedAgainstTwoDigits() {
        // Rates for apparel are published against "61", but a product carries a full code
        // like 61091000 - the walk up must reach the two-digit chapter.
        product.setHsnCode("61091000");
        when(rateRepo.findApplicable(eq("61091000"), anyString(), any())).thenReturn(List.of());
        when(rateRepo.findApplicable(eq("610910"), anyString(), any())).thenReturn(List.of());
        when(rateRepo.findApplicable(eq("6109"), anyString(), any())).thenReturn(List.of());
        when(rateRepo.findApplicable(eq("61"), eq(GstRateMaster.STATUS_VERIFIED), any()))
                .thenReturn(List.of(
                        conditional("61", 5.0, GstRateMaster.CONDITION_VALUE_UPTO, 2500.0),
                        conditional("61", 18.0, GstRateMaster.CONDITION_VALUE_ABOVE, 2500.0)));

        assertEquals(5.0, resolver.resolve(product, false, LocalDate.of(2026, 8, 6), 999.0).getTotalRate());
    }

    @Test
    void resolve_shouldNotResolve_whenOnlyOneBandIsPublishedAndPriceFallsOutsideIt() {
        // Footwear: only the "not exceeding Rs 2500 per pair" band is published. A costlier
        // pair must NOT silently borrow that rate.
        product.setHsnCode("64");
        when(rateRepo.findApplicable(eq("64"), eq(GstRateMaster.STATUS_VERIFIED), any()))
                .thenReturn(List.of(conditional("64", 5.0, GstRateMaster.CONDITION_VALUE_UPTO, 2500.0)));

        GstRateResolver.Resolved r = resolver.resolve(product, false, LocalDate.of(2026, 8, 6), 4999.0);
        assertFalse(r.isFromMaster(), "a price outside the published band must not resolve");
    }

    @Test
    void resolve_shouldNotResolve_whenConditionalRateButPriceUnknown() {
        product.setHsnCode("61");
        when(rateRepo.findApplicable(eq("61"), eq(GstRateMaster.STATUS_VERIFIED), any()))
                .thenReturn(List.of(
                        conditional("61", 5.0, GstRateMaster.CONDITION_VALUE_UPTO, 2500.0),
                        conditional("61", 18.0, GstRateMaster.CONDITION_VALUE_ABOVE, 2500.0)));

        assertTrue(resolver.findRate("61", LocalDate.of(2026, 8, 6), null).isEmpty(),
                "without a price neither band can be confirmed");
    }

    @Test
    void resolve_shouldNotResolve_whenHeadingHasSeveralUnconditionalRates() {
        // e.g. rice: 5% pre-packaged vs nil loose. Price cannot tell these apart.
        product.setHsnCode("1006");
        when(rateRepo.findApplicable(eq("1006"), eq(GstRateMaster.STATUS_VERIFIED), any()))
                .thenReturn(List.of(
                        rate("1006", 5.0, LocalDate.of(2025, 9, 22), null),
                        rate("1006", 0.0, LocalDate.of(2025, 9, 22), null)));

        assertTrue(resolver.findRate("1006", LocalDate.of(2026, 8, 6), 399.0).isEmpty());
    }

    private GstRateMaster packaged(String hsn, double pct, String type) {
        GstRateMaster r = rate(hsn, pct, LocalDate.of(2025, 9, 22), null);
        r.setConditionType(type);
        return r;
    }

    private void ricePublishedBothWays() {
        when(rateRepo.findApplicable(eq("1006"), eq(GstRateMaster.STATUS_VERIFIED), any()))
                .thenReturn(List.of(
                        packaged("1006", 5.0, GstRateMaster.CONDITION_PACKAGED),
                        packaged("1006", 0.0, GstRateMaster.CONDITION_UNPACKAGED)));
    }

    @Test
    void resolve_shouldChargeFivePercent_onPrePackagedRice() {
        product.setHsnCode("1006");
        product.setPrePackagedAndLabelled(true);
        ricePublishedBothWays();

        assertEquals(5.0, resolver.resolve(product, false, LocalDate.of(2026, 8, 6)).getTotalRate());
    }

    @Test
    void resolve_shouldChargeNil_onLooseRice() {
        product.setHsnCode("1006");
        product.setPrePackagedAndLabelled(false);
        ricePublishedBothWays();

        GstRateResolver.Resolved r = resolver.resolve(product, false, LocalDate.of(2026, 8, 6));
        assertEquals(0.0, r.getTotalRate());
        assertTrue(r.isFromMaster(), "nil is a real published rate, not a failure to resolve");
    }

    @Test
    void resolve_shouldNotGuess_whenPackagingIsUnknown() {
        // Neither side can be confirmed, and picking one would silently charge the wrong rate.
        product.setHsnCode("1006");
        product.setPrePackagedAndLabelled(null);
        ricePublishedBothWays();

        assertFalse(resolver.resolve(product, false, LocalDate.of(2026, 8, 6)).isFromMaster());
    }

    @Test
    void resolve_shouldTakePackagingFromTheProduct_withoutTheCallerPassingIt() {
        // Price has to be passed in by each caller, but packaging is a property of the product,
        // so the three-argument resolve must still get it right.
        product.setHsnCode("1006");
        product.setPrePackagedAndLabelled(true);
        ricePublishedBothWays();

        assertEquals(5.0, resolver.resolve(product, false, LocalDate.of(2026, 8, 6), 120.0).getTotalRate());
        assertEquals(5.0, resolver.resolve(product, false, LocalDate.of(2026, 8, 6)).getTotalRate());
    }

    @Test
    void resolve_shouldKeepValueBandsWorking_whenPackagingIsAlsoSet() {
        // Apparel is banded on price, not packaging; setting the packaging flag (which every
        // product now carries) must not disturb it.
        product.setHsnCode("61");
        product.setPrePackagedAndLabelled(true);
        when(rateRepo.findApplicable(eq("61"), eq(GstRateMaster.STATUS_VERIFIED), any()))
                .thenReturn(List.of(
                        conditional("61", 5.0, GstRateMaster.CONDITION_VALUE_UPTO, 2500.0),
                        conditional("61", 18.0, GstRateMaster.CONDITION_VALUE_ABOVE, 2500.0)));

        assertEquals(5.0, resolver.resolve(product, false, LocalDate.of(2026, 8, 6), 999.0).getTotalRate());
        assertEquals(18.0, resolver.resolve(product, false, LocalDate.of(2026, 8, 6), 4999.0).getTotalRate());
    }

    private GstRateMaster unverifiedLine(long id, String hsn, double pct) {
        GstRateMaster r = rate(hsn, pct, LocalDate.of(2025, 9, 22), null);
        r.setId(id);
        r.setStatus(GstRateMaster.STATUS_UNVERIFIED);
        return r;
    }

    @Test
    void resolve_shouldUseTheLineTheSellerPicked_evenThoughItIsUnverified() {
        // Unverified means "ambiguous for this heading in general", not "wrong". 0901 is 5%
        // roasted and nil green - the seller picking one IS the human decision the status was
        // waiting for, and it applies to their product only.
        product.setHsnCode("0901");
        product.setGstRateSelectionId(7L);
        when(rateRepo.findById(7L)).thenReturn(Optional.of(unverifiedLine(7L, "0901", 5.0)));

        GstRateResolver.Resolved r = resolver.resolve(product, false, LocalDate.of(2026, 8, 7));

        assertEquals(5.0, r.getTotalRate());
        assertTrue(r.isFromMaster());
    }

    @Test
    void resolve_shouldIgnoreASelectionForADifferentHeading() {
        // The product was re-coded after the choice was made, so the old answer is about
        // different goods entirely.
        product.setHsnCode("1006");
        product.setGstRateSelectionId(7L);
        when(rateRepo.findById(7L)).thenReturn(Optional.of(unverifiedLine(7L, "0901", 5.0)));
        when(rateRepo.findApplicable(anyString(), anyString(), any())).thenReturn(List.of());

        assertFalse(resolver.resolve(product, false, LocalDate.of(2026, 8, 7)).isFromMaster());
    }

    @Test
    void resolve_shouldIgnoreASelectionThatIsNoLongerInForce() {
        // A later notification closed the line off; the old choice must not outlive it.
        product.setHsnCode("0901");
        product.setGstRateSelectionId(7L);
        GstRateMaster expired = unverifiedLine(7L, "0901", 5.0);
        expired.setEffectiveTo(LocalDate.of(2026, 1, 31));
        when(rateRepo.findById(7L)).thenReturn(Optional.of(expired));
        when(rateRepo.findApplicable(anyString(), anyString(), any())).thenReturn(List.of());

        assertFalse(resolver.resolve(product, false, LocalDate.of(2026, 8, 7)).isFromMaster());
    }

    @Test
    void resolve_shouldIgnoreASelectionPointingAtARowThatIsGone() {
        product.setHsnCode("0901");
        product.setGstRateSelectionId(999L);
        when(rateRepo.findById(999L)).thenReturn(Optional.empty());
        when(rateRepo.findApplicable(anyString(), anyString(), any())).thenReturn(List.of());

        assertFalse(resolver.resolve(product, false, LocalDate.of(2026, 8, 7)).isFromMaster());
    }

    @Test
    void resolve_shouldAcceptASelectionMadeAgainstAParentHeading() {
        // Rates are published against "61" while a product carries 61091000; a choice made on
        // the heading still describes the same goods.
        product.setHsnCode("61091000");
        product.setGstRateSelectionId(7L);
        when(rateRepo.findById(7L)).thenReturn(Optional.of(unverifiedLine(7L, "61", 18.0)));

        assertEquals(18.0, resolver.resolve(product, false, LocalDate.of(2026, 8, 7)).getTotalRate());
    }
}
