package com.cauverystore.service;

import com.cauverystore.entities.ChapterMaster;
import com.cauverystore.entities.GstRateMaster;
import com.cauverystore.repository.GstRateMasterRepository;
import com.cauverystore.repository.ChapterMasterRepository;
import com.cauverystore.repository.SacMasterRepository;
import com.cauverystore.repository.TradeSynonymRepository;
import com.cauverystore.repository.CountryMasterRepository;
import com.cauverystore.repository.CurrencyMasterRepository;
import com.cauverystore.repository.PortMasterRepository;
import com.cauverystore.repository.PincodeStateRangeRepository;
import com.cauverystore.repository.HsnMasterRepository;
import com.cauverystore.repository.MasterUpdateLogRepository;
import com.cauverystore.repository.StateMasterRepository;
import com.cauverystore.repository.UnitMasterRepository;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers the rate seeding path against the real committed gst_rate_seed.json, because the
 * thing most likely to go wrong is a mismatch between what the seed says and what the loader
 * does with it - which a hand-built fixture would hide.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GstMasterDataLoaderTest {

    @Mock private HsnMasterRepository hsnRepo;
    @Mock private UnitMasterRepository unitRepo;
    @Mock private StateMasterRepository stateRepo;
    @Mock private GstRateMasterRepository rateRepo;
    @Mock private MasterUpdateLogRepository logRepo;
    @Mock private CountryMasterRepository countryRepo;
    @Mock private CurrencyMasterRepository currencyRepo;
    @Mock private PortMasterRepository portRepo;
    @Mock private PincodeStateRangeRepository pincodeRepo;
    @Mock private ChapterMasterRepository chapterRepo;
    @Mock private TradeSynonymRepository synonymRepo;
    @Mock private SacMasterRepository sacRepo;

    private GstMasterDataLoader loader;

    @BeforeEach
    void setUp() {
        loader = new GstMasterDataLoader(hsnRepo, unitRepo, stateRepo, rateRepo, logRepo,
                countryRepo, currencyRepo, portRepo, pincodeRepo, chapterRepo, synonymRepo, sacRepo);
    }

    private GstRateMaster autoVerified(String hsn, double rate) {
        GstRateMaster r = new GstRateMaster();
        r.setHsnCode(hsn);
        r.setGstRate(rate);
        r.setEffectiveFrom(LocalDate.of(2025, 9, 22));
        r.setStatus(GstRateMaster.STATUS_VERIFIED);
        r.setVerifiedBy("CBIC master data import");
        r.setVerifiedAt(LocalDateTime.now());
        return r;
    }

    @SuppressWarnings("unchecked")
    private List<GstRateMaster> runAndCaptureSaves() {
        ReflectionTestUtils.invokeMethod(loader, "loadGstRates");
        ArgumentCaptor<List<GstRateMaster>> captor = ArgumentCaptor.forClass(List.class);
        verify(rateRepo, atLeastOnce()).saveAll(captor.capture());
        List<GstRateMaster> all = new ArrayList<>();
        captor.getAllValues().forEach(all::addAll);
        return all;
    }

    @Test
    void shouldDemoteFootwearRateAutoVerifiedByAnEarlierSeed() {
        // The first seed approved footwear at a flat 5%, but only the "not exceeding Rs 2500
        // per pair" band is actually published - a costlier pair would have been undercharged.
        // Fixing the seed is not enough on its own: the load is insert-only, so the stale
        // approval has to be actively withdrawn from an already-populated database.
        GstRateMaster stale = autoVerified("64", 5.0);
        when(rateRepo.findAll()).thenReturn(List.of(stale));

        runAndCaptureSaves();

        assertEquals(GstRateMaster.STATUS_UNVERIFIED, stale.getStatus());
        assertNull(stale.getVerifiedBy());
        assertNull(stale.getVerifiedAt());
        assertTrue(stale.getNotes() != null && stale.getNotes().contains("Auto-verification withdrawn"),
                "the reason must be recorded on the row for whoever reviews it");
    }

    @Test
    void shouldLeaveHumanVerifiedRatesAlone() {
        // A CA's sign-off outranks the generator's guess and must survive a restart.
        GstRateMaster humanApproved = autoVerified("64", 5.0);
        humanApproved.setVerifiedBy("CA review 2026-08");
        when(rateRepo.findAll()).thenReturn(List.of(humanApproved));

        runAndCaptureSaves();

        assertEquals(GstRateMaster.STATUS_VERIFIED, humanApproved.getStatus());
        assertEquals("CA review 2026-08", humanApproved.getVerifiedBy());
    }

    @Test
    void shouldKeepAutoVerifiedRateTheCurrentSeedStillSupports() {
        // Only rows the seed has stopped standing behind get demoted; an unchanged, still
        // unambiguous heading must not be knocked out of service on every restart.
        GstRateMaster stillGood = autoVerified("0201", 0.0);
        when(rateRepo.findAll()).thenReturn(List.of(stillGood));

        runAndCaptureSaves();

        assertEquals(GstRateMaster.STATUS_VERIFIED, stillGood.getStatus(),
                "meat at nil is still what the seed says; it should not have been demoted");
    }

    @Test
    void shouldDemoteAFlatRateThatTheSeedNowExpressesAsASplit() {
        // Rice is 5% pre-packaged and nil loose. An earlier seed approved a flat 5% for the
        // whole heading; the current one publishes the two sides separately and decides
        // between them from the product's packaging. The flat approval has to go, or it would
        // sit alongside the split rows charging 5% on loose rice.
        GstRateMaster stale = autoVerified("1006", 5.0);
        when(rateRepo.findAll()).thenReturn(List.of(stale));

        runAndCaptureSaves();

        assertEquals(GstRateMaster.STATUS_UNVERIFIED, stale.getStatus());
    }

    @Test
    void shouldLoadBothApparelValueBandsAsVerified() {
        when(rateRepo.findAll()).thenReturn(List.of());

        List<GstRateMaster> saved = runAndCaptureSaves();

        List<GstRateMaster> apparel = saved.stream()
                .filter(r -> "61".equals(r.getHsnCode()))
                .toList();
        assertEquals(2, apparel.size(), "apparel needs both the <=2500 and >2500 bands");
        assertTrue(apparel.stream().allMatch(GstRateMaster::isVerified),
                "a complete pair of bands is decidable by price, so both are usable");
        assertTrue(apparel.stream().anyMatch(r -> r.getGstRate() == 5.0
                && GstRateMaster.CONDITION_VALUE_UPTO.equals(r.getConditionType())
                && r.getThresholdAmount() == 2500.0));
        assertTrue(apparel.stream().anyMatch(r -> r.getGstRate() == 18.0
                && GstRateMaster.CONDITION_VALUE_ABOVE.equals(r.getConditionType())
                && r.getThresholdAmount() == 2500.0));
    }

    @Test
    void shouldNotVerifyAHeadingWithOnlyOneValueBandPublished() {
        // Footwear: nothing tells us what a pair over Rs 2500 attracts, so the resolver must
        // not be handed a rate it would apply at every price.
        when(rateRepo.findAll()).thenReturn(List.of());

        List<GstRateMaster> saved = runAndCaptureSaves();

        saved.stream()
                .filter(r -> "64".equals(r.getHsnCode()))
                .forEach(r -> assertFalse(r.isVerified(),
                        "HSN 64 has only the lower band published - it needs review"));
    }

    @Test
    void shouldBeIdempotent_insertingNothingWhenEveryRowAlreadyExists() {
        // Restarting must not duplicate 1,375 rows. Feed back exactly what a first load
        // would have written and assert the second load writes no inserts.
        when(rateRepo.findAll()).thenReturn(List.of());
        List<GstRateMaster> firstLoad = runAndCaptureSaves();
        assertFalse(firstLoad.isEmpty());

        reset(rateRepo);
        when(rateRepo.findAll()).thenReturn(firstLoad);

        ReflectionTestUtils.invokeMethod(loader, "loadGstRates");

        verify(rateRepo, never()).saveAll(any());
    }

    @Test
    void shouldCloseOffARateThatALaterNotificationEnded() {
        // 2202 99 20 was replaced by 2202 99 21 / 29 from 01-05-2026. Left open-ended it would
        // go on resolving alongside its own replacements.
        GstRateMaster superseded = autoVerified("22029920", 5.0);
        when(rateRepo.findAll()).thenReturn(List.of(superseded));

        runAndCaptureSaves();

        assertEquals(LocalDate.of(2026, 4, 30), superseded.getEffectiveTo());
        assertTrue(superseded.getNotes() != null && superseded.getNotes().contains("Closed off"),
                "the reason has to be on the row, not only in the log");
    }

    @Test
    void shouldNotReopenARateAnAdminAlreadyClosedByHand() {
        // Someone who has read the notification themselves outranks the seed.
        GstRateMaster closedByHand = autoVerified("22029920", 5.0);
        closedByHand.setEffectiveTo(LocalDate.of(2026, 3, 31));
        when(rateRepo.findAll()).thenReturn(List.of(closedByHand));

        runAndCaptureSaves();

        assertEquals(LocalDate.of(2026, 3, 31), closedByHand.getEffectiveTo());
    }

    @Test
    void shouldLeaveRatesStillInForceOpenEnded() {
        GstRateMaster current = autoVerified("0201", 0.0);
        when(rateRepo.findAll()).thenReturn(List.of(current));

        runAndCaptureSaves();

        assertNull(current.getEffectiveTo(), "an open rate must not acquire an end date");
    }

    @Test
    void shouldRetractACodeThatIsNotARealHsnHeading() {
        // 1200 came from a mis-parsed code cell - chapter 12 runs 1201-1214. Removing it from
        // the seed does not reach a populated database, because the load is insert-only and the
        // demotion pass deliberately skips headings the seed no longer mentions.
        GstRateMaster fabricated = autoVerified("1200", 5.0);
        when(rateRepo.findAll()).thenReturn(List.of(fabricated));

        runAndCaptureSaves();

        assertEquals(GstRateMaster.STATUS_UNVERIFIED, fabricated.getStatus());
        assertNotNull(fabricated.getEffectiveTo(), "it has to stop resolving, not merely need review");
        assertTrue(fabricated.getEffectiveTo().isBefore(LocalDate.now()));
        assertTrue(fabricated.getNotes() != null && fabricated.getNotes().contains("Retracted"));
    }

    @Test
    void shouldRetractAFabricatedCodeEvenWhenAHumanVerifiedIt() {
        // Unlike a demotion, this is not "needs a second look" - no sign-off can make a heading
        // exist that the tariff does not contain.
        GstRateMaster signedOff = autoVerified("1200", 5.0);
        signedOff.setVerifiedBy("CA review 2026-08");
        when(rateRepo.findAll()).thenReturn(List.of(signedOff));

        runAndCaptureSaves();

        assertEquals(GstRateMaster.STATUS_UNVERIFIED, signedOff.getStatus());
        assertNull(signedOff.getVerifiedBy());
    }

    @Test
    void retractionShouldNotExtendAnEndDateSomeoneSetEarlier() {
        // Pushing the date forward would revive the row for the days in between.
        GstRateMaster closedEarlier = autoVerified("1200", 5.0);
        closedEarlier.setEffectiveTo(LocalDate.of(2026, 1, 31));
        when(rateRepo.findAll()).thenReturn(List.of(closedEarlier));

        runAndCaptureSaves();

        assertEquals(LocalDate.of(2026, 1, 31), closedEarlier.getEffectiveTo());
    }

    @Test
    void shouldNotInsertTheFabricatedCodeFromTheSeedAtAll() {
        // The belt to the retraction's braces: it must be gone from the committed seed too.
        when(rateRepo.findAll()).thenReturn(List.of());

        List<GstRateMaster> saved = runAndCaptureSaves();

        assertTrue(saved.stream().noneMatch(r -> "1200".equals(r.getHsnCode())));
        assertTrue(saved.stream().anyMatch(r -> "2101".equals(r.getHsnCode())),
                "the goods it described belong to 2101, which must still be seeded");
    }

    @SuppressWarnings("unchecked")
    private List<ChapterMaster> runChapterLoad() {
        ReflectionTestUtils.invokeMethod(loader, "loadChapters");
        ArgumentCaptor<List<ChapterMaster>> captor = ArgumentCaptor.forClass(List.class);
        verify(chapterRepo, atLeastOnce()).saveAll(captor.capture());
        List<ChapterMaster> all = new ArrayList<>();
        captor.getAllValues().forEach(all::addAll);
        return all;
    }

    @Test
    void shouldNameEveryChapterTheRateSeedPrices() {
        // The point of the chapter master: 36 chapter codes are priced by the seed, and each one
        // has to be showable to a seller as a trade rather than as two digits.
        when(chapterRepo.findAll()).thenReturn(List.of());
        when(chapterRepo.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        List<ChapterMaster> loaded = runChapterLoad();
        Map<String, ChapterMaster> byCode = new HashMap<>();
        loaded.forEach(c -> byCode.put(c.getChapter(), c));

        for (String priced : List.of("61", "62", "64", "03", "10", "92", "87")) {
            assertTrue(byCode.containsKey(priced), "chapter " + priced + " is priced but missing");
            assertTrue(byCode.get(priced).isNamed(), "chapter " + priced + " has no title");
        }
        assertEquals("ARTICLES OF APPAREL AND CLOTHING ACCESSORIES, KNITTED OR CROCHETED",
                byCode.get("61").getTitle());
    }

    @Test
    void shouldKeepAChapterThatHasNoPublishedTitle() {
        // Five chapters carry no title anywhere in the master. Dropping them would silently
        // shorten the tariff; writing in a remembered title would put unsourced text into the
        // data that decides tax. The row exists and says so.
        when(chapterRepo.findAll()).thenReturn(List.of());
        when(chapterRepo.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        ChapterMaster dairy = runChapterLoad().stream()
                .filter(c -> "04".equals(c.getChapter()))
                .findFirst().orElseThrow(() -> new AssertionError("chapter 04 was dropped"));

        assertNull(dairy.getTitle());
        assertFalse(dairy.isNamed());
        assertEquals("04", dairy.getLabel(), "an unnamed chapter still labels as its number");
    }

    @Test
    void shouldNotBlankATitleAlreadyStoredWhenTheFileHasNone() {
        // Someone may fill in one of the five by hand. A later run reading the same file must
        // not wipe that back to null.
        ChapterMaster filledIn = new ChapterMaster("04", "DAIRY PRODUCE", "entered by hand");
        when(chapterRepo.findAll()).thenReturn(List.of(filledIn));
        when(chapterRepo.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        ReflectionTestUtils.invokeMethod(loader, "loadChapters");

        assertEquals("DAIRY PRODUCE", filledIn.getTitle());
    }

    @Test
    void chapterLoadShouldBeIdempotent() {
        when(chapterRepo.findAll()).thenReturn(List.of());
        when(chapterRepo.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        List<ChapterMaster> first = runChapterLoad();
        assertEquals(98, first.size(), "01-99 less chapter 77, which the tariff reserves");

        reset(chapterRepo);
        when(chapterRepo.findAll()).thenReturn(first);

        ReflectionTestUtils.invokeMethod(loader, "loadChapters");

        verify(chapterRepo, never()).saveAll(any());
    }

    @Test
    void shouldPriceEveryHeadingInASchedulesRange() {
        // CBIC writes one entry against a span - "5208 to 5212, woven fabrics of cotton". The
        // extraction kept 5208 and dropped the rest, so a cotton lungi at 52095110 walked past
        // the missing 5209 to chapter 52, whose only entry is the Gandhi Topi and khadi yarn
        // exemption, and was invoiced at nil instead of 5%.
        when(rateRepo.findAll()).thenReturn(List.of());

        Map<String, List<GstRateMaster>> byCode = new HashMap<>();
        runAndCaptureSaves().forEach(r -> byCode.computeIfAbsent(r.getHsnCode(),
                k -> new ArrayList<>()).add(r));

        for (String heading : List.of("5208", "5209", "5210", "5211", "5212")) {
            List<GstRateMaster> rows = byCode.get(heading);
            assertNotNull(rows, "heading " + heading + " is inside a priced range but has no row");
            assertTrue(rows.stream().anyMatch(r -> r.getGstRate() == 5.0),
                    "cotton fabric heading " + heading + " should be 5%");
        }
        // The polymer span was the costliest: 18% goods falling to a 5% chapter entry.
        assertTrue(byCode.get("3913").stream().anyMatch(r -> r.getGstRate() == 18.0));
    }

    @Test
    void shouldMarkOnlyGenuinelyChapterWideEntriesAsSuch() {
        // Most chapter-level entries name particular goods. If they load as chapter-wide, the
        // resolver charges them to everything beneath - nil on jewellery, 5% on plastics.
        when(rateRepo.findAll()).thenReturn(List.of());

        List<GstRateMaster> chapterRows = runAndCaptureSaves().stream()
                .filter(r -> r.getHsnCode().length() == 2)
                .toList();

        assertFalse(chapterRows.isEmpty());
        for (GstRateMaster r : chapterRows) {
            if (List.of("60", "61", "62").contains(r.getHsnCode())) continue;
            assertFalse(r.coversWholeChapter(),
                    "chapter " + r.getHsnCode() + " names specific goods (" + r.getConditionText()
                            + ") and must not price the whole chapter");
        }
        assertTrue(chapterRows.stream()
                        .filter(r -> "61".equals(r.getHsnCode()))
                        .allMatch(GstRateMaster::coversWholeChapter),
                "chapter 61 is apparel outright and does price everything beneath it");
    }

    @Test
    void shouldCarryTheSeedsEndDateOntoNewlyInsertedRows() {
        when(rateRepo.findAll()).thenReturn(List.of());

        List<GstRateMaster> saved = runAndCaptureSaves();

        GstRateMaster inserted = saved.stream()
                .filter(r -> "22029920".equals(r.getHsnCode()))
                .findFirst().orElseThrow();
        assertEquals(LocalDate.of(2026, 4, 30), inserted.getEffectiveTo());
    }

    @Test
    void shouldSetTheWholeChapterFlagOnRowsThatAlreadyExist() {
        // The regression this prevents: the load is insert-only, so chapter 61's rows in an
        // already-populated database keep a null flag, the resolver refuses a chapter row that
        // does not claim to cover its chapter, and every garment stops being sellable.
        GstRateMaster apparelLower = autoVerified("61", 5.0);
        apparelLower.setConditionType(GstRateMaster.CONDITION_VALUE_UPTO);
        apparelLower.setThresholdAmount(2500.0);
        assertNull(apparelLower.getWholeChapter(), "starts as a pre-existing row would");
        when(rateRepo.findAll()).thenReturn(List.of(apparelLower));

        runAndCaptureSaves();

        assertTrue(apparelLower.coversWholeChapter(),
                "chapter 61 is apparel outright and must go on pricing everything beneath it");
    }

    @Test
    void shouldTakeTheFlagAwayWhenTheSeedNoLongerClaimsTheChapter() {
        // Otherwise a chapter dropped from that short list would go on answering for goods it
        // does not name, on the strength of a flag set months earlier.
        GstRateMaster narrowChapterRow = autoVerified("71", 0.0);
        narrowChapterRow.setWholeChapter(Boolean.TRUE);
        when(rateRepo.findAll()).thenReturn(List.of(narrowChapterRow));

        runAndCaptureSaves();

        assertFalse(narrowChapterRow.coversWholeChapter(),
                "chapter 71's entry is the Reserve Bank exemption, not a rate for all jewellery");
    }
}
