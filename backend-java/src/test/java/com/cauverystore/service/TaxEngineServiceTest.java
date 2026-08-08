package com.cauverystore.service;

import com.cauverystore.entities.Product;
import com.cauverystore.entities.SacMaster;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.repository.SacMasterRepository;
import com.cauverystore.util.GstComplianceUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The engine was specified with the place-of-supply rule wrong - "if a buyer GSTIN is present,
 * use the buyer's state". Holding a GSTIN does not move the place of supply; only a delivery
 * made to a third person on the buyer's direction does, under section 10(1)(b).
 *
 * These tests exist mostly to hold that line, because the mistaken version looks reasonable and
 * produces the right answer for the common case where a buyer takes delivery at their own
 * registered address. It goes wrong exactly where it matters.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaxEngineServiceTest {

    @Mock private ProductRepository productRepo;
    @Mock private SacMasterRepository sacRepo;
    @Mock private GstRateResolver rateResolver;

    private TaxEngineService engine;

    @BeforeEach
    void setUp() {
        engine = new TaxEngineService(productRepo, sacRepo, rateResolver);
        Product p = new Product();
        p.setId(1L);
        p.setName("Cotton Lungi");
        p.setHsnCode("52095110");
        p.setPrice(1000.0);
        when(productRepo.findById(1L)).thenReturn(Optional.of(p));
        when(rateResolver.findRate(any(), any(), any(), any())).thenReturn(Optional.of(5.0));
    }

    private TaxEngineService.Query goods(String buyerState, String deliveryState,
                                         String gstin, boolean billToShipTo) {
        return new TaxEngineService.Query(1L, null, false, "33", buyerState, deliveryState,
                gstin, billToShipTo, 1000.0, LocalDate.of(2026, 8, 8));
    }

    @Test
    void aRegisteredBuyerTakingDeliveryElsewhereIsStillTaxedOnTheDeliveryState() {
        // The case the specification got wrong. A Chennai company buying from a Chennai seller
        // for delivery to its own Bengaluru branch is an INTER-state supply - place of supply
        // Karnataka. Reading the buyer's GSTIN state would have made it intra-state and charged
        // CGST plus SGST: the wrong head, unusable as credit, reported in the wrong state.
        Map<String, Object> out = engine.resolve(goods("33", "29", "33ABCDE1234F1Z5", false));

        assertEquals("29", out.get("place_of_supply"));
        assertEquals("INTER_STATE", out.get("supply_type"));
        assertEquals(50.0, out.get("igst"));
        assertEquals(0.0, out.get("cgst"));
        assertTrue(String.valueOf(out.get("place_of_supply_rule")).contains("10(1)(a)"));
    }

    @Test
    void aDeclaredBillToShipToUsesTheBuyersState() {
        // Section 10(1)(b). Identical states to the test above; only the declaration differs.
        Map<String, Object> out = engine.resolve(goods("33", "29", "33ABCDE1234F1Z5", true));

        assertEquals("33", out.get("place_of_supply"));
        assertEquals("INTRA_STATE", out.get("supply_type"));
        assertEquals(25.0, out.get("cgst"));
        assertEquals(25.0, out.get("sgst"));
        assertEquals(0.0, out.get("igst"));
        assertTrue(String.valueOf(out.get("place_of_supply_rule")).contains("10(1)(b)"));
    }

    @Test
    void billToShipToIsIgnoredForAnUnregisteredBuyer() {
        // 10(1)(b) turns on a third person's principal place of business. URP has none.
        Map<String, Object> out = engine.resolve(goods("33", "29", "URP", true));

        assertEquals("29", out.get("place_of_supply"));
        assertEquals("INTER_STATE", out.get("supply_type"));
    }

    @Test
    void ordinaryB2cUsesTheDeliveryState() {
        Map<String, Object> out = engine.resolve(goods(null, "33", null, false));

        assertEquals("33", out.get("place_of_supply"));
        assertEquals("INTRA_STATE", out.get("supply_type"));
        assertEquals(25.0, out.get("cgst"));
    }

    @Test
    void aServiceResolvesFromTheSacMaster() {
        SacMaster courier = new SacMaster("996511", "Road transport of goods", 18.0,
                LocalDate.of(2025, 9, 22), "Notif. 11/2017-CT(Rate)", "VERIFIED");
        when(sacRepo.findApplicable(eq("996511"), any())).thenReturn(List.of(courier));

        Map<String, Object> out = engine.resolve(new TaxEngineService.Query(
                null, "996511", true, "33", "33", "33", null, false, 100.0, LocalDate.of(2026, 8, 8)));

        assertEquals("996511", out.get("hsn_or_sac"));
        assertEquals(18.0, out.get("gst_rate"));
        assertEquals(9.0, out.get("cgst"));
        assertEquals(9.0, out.get("sgst"));
        assertEquals(18.0, out.get("total_tax"));
    }

    @Test
    void anUnapprovedServiceRateIsRefusedRatherThanGuessed() {
        // Same posture as goods. A service nobody has priced cannot be billed.
        when(sacRepo.findApplicable(any(), any())).thenReturn(List.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> engine.resolve(new TaxEngineService.Query(
                        null, "999799", true, "33", "33", "33", null, false, 100.0, null)));
        assertTrue(ex.getMessage().contains("No approved GST rate"));
    }

    @Test
    void theSplitAlwaysSumsToTheRate() {
        // Whatever the head, the customer pays the same. A split that does not add up means one
        // of the three was computed from a different rate.
        Map<String, Object> intra = engine.resolve(goods("33", "33", null, false));
        Map<String, Object> inter = engine.resolve(goods("33", "29", null, false));

        assertEquals(50.0, intra.get("total_tax"));
        assertEquals(50.0, inter.get("total_tax"));
    }

    @Test
    void panFormatIsCheckedByPositionNotJustLength() {
        // "10 alphanumeric" would accept 1234567890, which was never issued to anybody.
        assertDoesNotThrow(() -> GstComplianceUtil.validatePan("ABCDE1234F"));
        assertThrows(IllegalArgumentException.class, () -> GstComplianceUtil.validatePan("1234567890"));
        assertThrows(IllegalArgumentException.class, () -> GstComplianceUtil.validatePan("ABCDE1234"));
        assertDoesNotThrow(() -> GstComplianceUtil.validatePan(null));   // optional field
    }

    @Test
    void aPanShouldAgreeWithThePanInsideTheGstin() {
        // A GSTIN carries its holder's PAN at characters 3-12, so the two can be checked against
        // each other rather than stored as unrelated claims.
        assertTrue(GstComplianceUtil.panMatchesGstin("ABCDE1234F", "33ABCDE1234F1Z5"));
        assertFalse(GstComplianceUtil.panMatchesGstin("ZZZZZ9999Z", "33ABCDE1234F1Z5"));
    }
}
