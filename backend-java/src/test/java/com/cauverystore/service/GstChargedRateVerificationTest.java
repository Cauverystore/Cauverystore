package com.cauverystore.service;

import com.cauverystore.entities.GstRateMaster;
import com.cauverystore.entities.Product;
import com.cauverystore.repository.GstRateMasterRepository;
import com.cauverystore.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Answers one question with evidence rather than assurance: is an order charged the GST that
 * CBIC actually published for those goods?
 *
 * Every other test here mocks the rate rows, which proves the arithmetic but not the data. This
 * one loads the committed seed exactly as the application does, feeds it to the real resolver,
 * and checks a basket of goods whose correct rate is a matter of public record. A wrong rate in
 * the seed shows up here as a failure naming the goods, which is the only way this class of
 * error gets caught before a customer is overcharged.
 *
 * Every expectation below traces to Notification 09/2025-Central Tax (Rate) as amended, and the
 * schedule rates there are CGST halves - Schedule I at 2.5% is the 5% asserted here.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GstChargedRateVerificationTest {

    @Mock private com.cauverystore.repository.HsnMasterRepository hsnRepo;
    @Mock private com.cauverystore.repository.UnitMasterRepository unitRepo;
    @Mock private com.cauverystore.repository.StateMasterRepository stateRepo;
    @Mock private GstRateMasterRepository rateRepo;
    @Mock private com.cauverystore.repository.MasterUpdateLogRepository logRepo;
    @Mock private com.cauverystore.repository.CountryMasterRepository countryRepo;
    @Mock private com.cauverystore.repository.CurrencyMasterRepository currencyRepo;
    @Mock private com.cauverystore.repository.PortMasterRepository portRepo;
    @Mock private com.cauverystore.repository.PincodeStateRangeRepository pincodeRepo;
    @Mock private com.cauverystore.repository.ChapterMasterRepository chapterRepo;
    @Mock private com.cauverystore.repository.TradeSynonymRepository synonymRepo;
    @Mock private ProductRepository productRepo;

    private GstRateResolver resolver;
    private final Map<String, List<GstRateMaster>> loaded = new HashMap<>();

    /** Loads the real seed once, then serves it the way the repository would. */
    @BeforeEach
    @SuppressWarnings("unchecked")
    void loadTheRealSeed() {
        GstMasterDataLoader loader = new GstMasterDataLoader(hsnRepo, unitRepo, stateRepo, rateRepo,
                logRepo, countryRepo, currencyRepo, portRepo, pincodeRepo, chapterRepo, synonymRepo);
        when(rateRepo.findAll()).thenReturn(List.of());
        ReflectionTestUtils.invokeMethod(loader, "loadGstRates");

        ArgumentCaptor<List<GstRateMaster>> captor = ArgumentCaptor.forClass(List.class);
        verify(rateRepo, atLeastOnce()).saveAll(captor.capture());
        loaded.clear();
        captor.getAllValues().forEach(batch -> batch.forEach(
                r -> loaded.computeIfAbsent(r.getHsnCode(), k -> new ArrayList<>()).add(r)));

        resolver = new GstRateResolver(rateRepo);
        // findApplicable is what the resolver calls: verified rows for a code, in force on a date.
        when(rateRepo.findApplicable(any(), any(), any())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            String status = inv.getArgument(1);
            LocalDate on = inv.getArgument(2);
            return loaded.getOrDefault(code, List.of()).stream()
                    .filter(r -> status.equals(r.getStatus()))
                    .filter(r -> r.getEffectiveFrom() == null || !r.getEffectiveFrom().isAfter(on))
                    .filter(r -> r.getEffectiveTo() == null || !r.getEffectiveTo().isBefore(on))
                    .toList();
        });
    }

    private Optional<Double> rateFor(String hsn, Double unitPrice, Boolean prePackaged) {
        return resolver.findRate(hsn, LocalDate.of(2026, 8, 8), unitPrice, prePackaged);
    }

    private void assertCharged(String what, String hsn, Double price, Boolean packed, double expected) {
        Optional<Double> actual = rateFor(hsn, price, packed);
        assertTrue(actual.isPresent(),
                what + " (HSN " + hsn + ") resolves to no rate at all, so it cannot be invoiced.");
        assertEquals(expected, actual.get(), 0.001,
                what + " (HSN " + hsn + ") should be charged " + expected + "% but resolves to "
                        + actual.get() + "%.");
    }

    @Test
    void footwearUnderTwoThousandFiveHundred_shouldBeFivePercent() {
        // The bug this was written for. The 5% carve-out sat against chapter 64 while the
        // headings carried a flat 18%, and the resolver stops at the heading - so every pair
        // was taxed 18%, a 13-point overcharge on the cheapest footwear sold.
        assertCharged("A Rs 499 pair of rubber slippers", "64019200", 499.0, null, 5.0);
        assertCharged("A Rs 1,200 pair of leather shoes", "64031990", 1200.0, null, 5.0);
        assertCharged("A pair priced exactly at the threshold", "64029990", 2500.0, null, 5.0);
    }

    @Test
    void footwearAboveTwoThousandFiveHundred_shouldBeEighteenPercent() {
        assertCharged("A Rs 4,999 pair of leather boots", "64031990", 4999.0, null, 18.0);
        assertCharged("A pair a rupee over the threshold", "64029990", 2500.01, null, 18.0);
    }

    @Test
    void apparel_shouldFollowTheSameValueBand() {
        assertCharged("A Rs 799 cotton shirt", "62052000", 799.0, null, 5.0);
        assertCharged("A Rs 3,500 jacket", "62019000", 3500.0, null, 18.0);
        assertCharged("A Rs 450 knitted t-shirt", "61091000", 450.0, null, 5.0);
    }

    @Test
    void staples_shouldFollowPackaging() {
        // Rice is 5% pre-packaged and labelled, nil loose. Same code either way, so only the
        // packaging answer separates them.
        assertCharged("Pre-packaged branded rice", "10063010", 250.0, Boolean.TRUE, 5.0);
        assertCharged("Loose rice sold by weight", "10063010", 250.0, Boolean.FALSE, 0.0);
    }

    @Test
    void cottonLungi_shouldBeFivePercent() {
        // Resolved to nil until the collapsed 5208-to-5212 range was expanded, because 5209 was
        // missing and the walk fell to chapter 52's khadi-yarn exemption.
        assertCharged("A cotton lungi", "52095110", 450.0, null, 5.0);
        assertCharged("A handloom lungi", "52095111", 450.0, null, 5.0);
    }

    @Test
    void preciousMetalsAndStones_shouldUseTheSpecialRates() {
        assertCharged("Gold jewellery", "71131110", 45000.0, null, 3.0);
        assertCharged("Silver articles", "71141110", 5000.0, null, 3.0);
        assertCharged("Cut and polished precious stones", "71031010", 20000.0, null, 0.25);
    }

    @Test
    void demeritGoods_shouldBeFortyPercent() {
        // GST 2.0 moved these to 40%. A stale 28% here would understate by 12 points.
        assertCharged("Cigarettes", "24022010", 300.0, null, 40.0);
        assertCharged("Chewing tobacco", "24039910", 100.0, null, 40.0);
        assertCharged("Carbonated fruit drink", "22021010", 40.0, null, 40.0);
    }

    @Test
    void everydayElectronics_shouldBeEighteenPercent() {
        assertCharged("A mobile phone", "85171300", 24999.0, null, 18.0);
        assertCharged("A laptop", "84713010", 55000.0, null, 18.0);
    }

    @Test
    void freshProduceAndMilk_shouldBeNil() {
        assertCharged("Fresh potatoes", "07019000", 40.0, null, 0.0);
        assertCharged("Fresh milk", "04011000", 30.0, null, 0.0);
    }

    @Test
    void medicines_shouldBeFivePercent() {
        assertCharged("A packet of medicine", "30049099", 120.0, null, 5.0);
    }

    @Test
    void noRateShouldBeOutsideThePublishedSlabs() {
        // GST 2.0 has no 12% and no 28%. A row at either is a rate from the old regime that
        // survived, and it would be charged to customers as though it were current.
        List<Double> allowed = List.of(0.0, 0.25, 1.5, 3.0, 5.0, 18.0, 40.0);
        List<String> offenders = new ArrayList<>();
        loaded.values().forEach(rows -> rows.stream()
                .filter(r -> !allowed.contains(r.getGstRate()))
                .forEach(r -> offenders.add(r.getHsnCode() + " @ " + r.getGstRate() + "%")));

        assertTrue(offenders.isEmpty(),
                "these rates are outside the GST 2.0 slabs: " + offenders);
    }

    @Test
    void aChapterEntryForNamedGoods_shouldNotPriceTheWholeChapter() {
        // Chapter 71's chapter-level entry is "rupee notes or coins sold to the Reserve Bank" at
        // nil. If it ever answers for jewellery again, this catches it before an invoice does.
        Optional<Double> rate = rateFor("71179090", 500.0, null);
        assertFalse(rate.isPresent() && rate.get() == 0.0,
                "imitation jewellery must not inherit the Reserve Bank exemption");
    }

    @Test
    void everySellableCodeShouldResolveOrRefuse_neverGuess() {
        // The resolver has no fallback. This documents that an unknown code raises rather than
        // returning something plausible, which is what makes an unresolved rate visible.
        Product mystery = new Product();
        mystery.setName("Something uncatalogued");
        mystery.setHsnCode("99999999");
        assertThrows(GstRateResolver.GstRateUnresolvedException.class,
                () -> resolver.resolve(mystery, false, LocalDate.of(2026, 8, 8), 100.0));
    }

    @Test
    void staleFlatFootwearRowsInTheDatabase_shouldNotOverrideTheNewBands() {
        // What production will actually look like after this deploys. The load is insert-only,
        // so the old unconditional 18% rows for 6401-6405 stay put and the two new bands are
        // inserted alongside them. This asserts the outcome of that mixture rather than
        // assuming it: a matching value condition has to beat an unconditional row, or a Rs 499
        // pair would go on being charged 18% forever.
        GstRateMaster staleFlat = new GstRateMaster();
        staleFlat.setHsnCode("6403");
        staleFlat.setGstRate(18.0);
        staleFlat.setEffectiveFrom(LocalDate.of(2025, 9, 22));
        staleFlat.setStatus(GstRateMaster.STATUS_VERIFIED);
        loaded.computeIfAbsent("6403", k -> new ArrayList<>()).add(staleFlat);

        assertEquals(3, loaded.get("6403").size(), "the stale row plus both new bands");
        assertEquals(Optional.of(5.0), rateFor("64031990", 499.0, null),
                "a cheap pair must take the 5% band despite the stale flat 18% row");
        assertEquals(Optional.of(18.0), rateFor("64031990", 4999.0, null));
    }
}
