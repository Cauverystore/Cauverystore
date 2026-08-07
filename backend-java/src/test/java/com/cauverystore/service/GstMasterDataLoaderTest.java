package com.cauverystore.service;

import com.cauverystore.entities.GstRateMaster;
import com.cauverystore.repository.GstRateMasterRepository;
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
import java.util.List;

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

    private GstMasterDataLoader loader;

    @BeforeEach
    void setUp() {
        loader = new GstMasterDataLoader(hsnRepo, unitRepo, stateRepo, rateRepo, logRepo,
                countryRepo, currencyRepo, portRepo, pincodeRepo);
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
    void shouldCarryTheSeedsEndDateOntoNewlyInsertedRows() {
        when(rateRepo.findAll()).thenReturn(List.of());

        List<GstRateMaster> saved = runAndCaptureSaves();

        GstRateMaster inserted = saved.stream()
                .filter(r -> "22029920".equals(r.getHsnCode()))
                .findFirst().orElseThrow();
        assertEquals(LocalDate.of(2026, 4, 30), inserted.getEffectiveTo());
    }
}
