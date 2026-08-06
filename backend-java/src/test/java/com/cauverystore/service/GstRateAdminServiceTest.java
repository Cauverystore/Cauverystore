package com.cauverystore.service;

import com.cauverystore.entities.GstRateMaster;
import com.cauverystore.repository.GstRateMasterRepository;
import com.cauverystore.repository.HsnMasterRepository;
import com.cauverystore.repository.MasterUpdateLogRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GstRateAdminServiceTest {

    @Mock private GstRateMasterRepository rateRepo;
    @Mock private HsnMasterRepository hsnRepo;
    @Mock private MasterUpdateLogRepository logRepo;
    @Mock private AuthorizationService authorizationService;

    private GstRateAdminService service;

    @BeforeEach
    void setUp() {
        service = new GstRateAdminService(rateRepo, hsnRepo, logRepo, authorizationService);
        when(authorizationService.getCurrentUserEmail()).thenReturn("admin@cauverystore.in");
        when(rateRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(hsnRepo.findById(anyString())).thenReturn(Optional.empty());
    }

    private GstRateMaster rate(long id, String hsn, double pct, String status) {
        GstRateMaster r = new GstRateMaster();
        r.setId(id);
        r.setHsnCode(hsn);
        r.setGstRate(pct);
        r.setEffectiveFrom(LocalDate.of(2025, 9, 22));
        r.setStatus(status);
        return r;
    }

    @Test
    void verify_shouldRecordWhoApprovedItAndWhen() {
        GstRateMaster r = rate(1L, "1006", 5.0, GstRateMaster.STATUS_UNVERIFIED);
        when(rateRepo.findById(1L)).thenReturn(Optional.of(r));
        when(rateRepo.findByHsnCodeOrderByEffectiveFromDesc("1006")).thenReturn(List.of(r));

        GstRateMaster saved = service.verify(1L, "Pre-packaged rice, per Notif. 09/2025 Sch. I");

        assertEquals(GstRateMaster.STATUS_VERIFIED, saved.getStatus());
        assertEquals("admin@cauverystore.in", saved.getVerifiedBy());
        assertNotNull(saved.getVerifiedAt());
        assertTrue(saved.getNotes().contains("Notif. 09/2025"));
    }

    @Test
    void verify_shouldRefuse_whenItWouldLeaveTwoFlatRatesInForceAtOnce() {
        // The resolver refuses to choose between two unconditional rates and falls back to the
        // default instead - so this approval would look successful while changing nothing.
        GstRateMaster candidate = rate(2L, "1006", 0.0, GstRateMaster.STATUS_UNVERIFIED);
        GstRateMaster alreadyLive = rate(1L, "1006", 5.0, GstRateMaster.STATUS_VERIFIED);
        when(rateRepo.findById(2L)).thenReturn(Optional.of(candidate));
        when(rateRepo.findByHsnCodeOrderByEffectiveFromDesc("1006"))
                .thenReturn(List.of(alreadyLive, candidate));

        GstRateAdminService.RateReviewException ex = assertThrows(
                GstRateAdminService.RateReviewException.class, () -> service.verify(2L, null));

        assertTrue(ex.getMessage().contains("5.0%"), "should name the rate it clashes with");
        assertEquals(GstRateMaster.STATUS_UNVERIFIED, candidate.getStatus());
        verify(rateRepo, never()).save(any());
    }

    @Test
    void verify_shouldAllowBothValueBands_sincePriceTellsThemApart() {
        GstRateMaster lower = rate(1L, "61", 5.0, GstRateMaster.STATUS_VERIFIED);
        lower.setConditionType(GstRateMaster.CONDITION_VALUE_UPTO);
        lower.setThresholdAmount(2500.0);
        GstRateMaster upper = rate(2L, "61", 18.0, GstRateMaster.STATUS_UNVERIFIED);
        upper.setConditionType(GstRateMaster.CONDITION_VALUE_ABOVE);
        upper.setThresholdAmount(2500.0);

        when(rateRepo.findById(2L)).thenReturn(Optional.of(upper));
        when(rateRepo.findByHsnCodeOrderByEffectiveFromDesc("61")).thenReturn(List.of(lower, upper));

        assertEquals(GstRateMaster.STATUS_VERIFIED, service.verify(2L, null).getStatus());
    }

    @Test
    void verify_shouldAllowASuccessorRate_whenTheOldOneWasClosedOff() {
        // A superseded rate must not block its replacement; the periods do not overlap.
        GstRateMaster old = rate(1L, "1006", 12.0, GstRateMaster.STATUS_VERIFIED);
        old.setEffectiveFrom(LocalDate.of(2017, 7, 1));
        old.setEffectiveTo(LocalDate.of(2025, 9, 21));
        GstRateMaster current = rate(2L, "1006", 5.0, GstRateMaster.STATUS_UNVERIFIED);

        when(rateRepo.findById(2L)).thenReturn(Optional.of(current));
        when(rateRepo.findByHsnCodeOrderByEffectiveFromDesc("1006")).thenReturn(List.of(old, current));

        assertEquals(GstRateMaster.STATUS_VERIFIED, service.verify(2L, null).getStatus());
    }

    @Test
    void unverify_shouldRequireAReason() {
        GstRateMaster r = rate(1L, "1006", 5.0, GstRateMaster.STATUS_VERIFIED);
        when(rateRepo.findById(1L)).thenReturn(Optional.of(r));

        assertThrows(GstRateAdminService.RateReviewException.class, () -> service.unverify(1L, "  "));
        assertEquals(GstRateMaster.STATUS_VERIFIED, r.getStatus());
    }

    @Test
    void unverify_shouldClearApprovalAndRecordTheReason() {
        GstRateMaster r = rate(1L, "1006", 5.0, GstRateMaster.STATUS_VERIFIED);
        r.setVerifiedBy("someone@cauverystore.in");
        r.setVerifiedAt(LocalDateTime.now());
        when(rateRepo.findById(1L)).thenReturn(Optional.of(r));

        GstRateMaster saved = service.unverify(1L, "Superseded by Notif. 01/2026");

        assertEquals(GstRateMaster.STATUS_UNVERIFIED, saved.getStatus());
        assertNull(saved.getVerifiedBy());
        assertNull(saved.getVerifiedAt());
        assertTrue(saved.getNotes().contains("Superseded by Notif. 01/2026"));
    }

    @Test
    void update_shouldSendAVerifiedRateBackForReapproval() {
        // An approval attests to the values that were read; changing them invalidates it.
        GstRateMaster r = rate(1L, "1006", 5.0, GstRateMaster.STATUS_VERIFIED);
        r.setVerifiedBy("someone@cauverystore.in");
        r.setVerifiedAt(LocalDateTime.now());
        when(rateRepo.findById(1L)).thenReturn(Optional.of(r));

        GstRateMaster edit = new GstRateMaster();
        edit.setGstRate(18.0);

        GstRateMaster saved = service.update(1L, edit);

        assertEquals(18.0, saved.getGstRate());
        assertEquals(GstRateMaster.STATUS_UNVERIFIED, saved.getStatus());
        assertNull(saved.getVerifiedBy());
    }

    @Test
    void create_shouldArriveUnverifiedEvenThoughAnAdminEnteredIt() {
        GstRateMaster input = new GstRateMaster();
        input.setHsnCode("64");
        input.setGstRate(18.0);
        input.setEffectiveFrom(LocalDate.of(2025, 9, 22));
        input.setConditionType(GstRateMaster.CONDITION_VALUE_ABOVE);
        input.setThresholdAmount(2500.0);
        input.setThresholdUnit("pair");

        GstRateMaster saved = service.create(input);

        assertEquals(GstRateMaster.STATUS_UNVERIFIED, saved.getStatus());
        assertEquals(GstRateMaster.CONDITION_VALUE_ABOVE, saved.getConditionType());
        assertTrue(saved.getNotes().contains("admin@cauverystore.in"));
    }

    @Test
    void create_shouldRejectAConditionWithNoThreshold() {
        GstRateMaster input = new GstRateMaster();
        input.setHsnCode("64");
        input.setGstRate(18.0);
        input.setEffectiveFrom(LocalDate.of(2025, 9, 22));
        input.setConditionType(GstRateMaster.CONDITION_VALUE_ABOVE);

        GstRateAdminService.RateReviewException ex = assertThrows(
                GstRateAdminService.RateReviewException.class, () -> service.create(input));
        assertTrue(ex.getMessage().contains("threshold"));
    }

    @Test
    void create_shouldRejectAnImpossibleRate() {
        GstRateMaster input = new GstRateMaster();
        input.setHsnCode("64");
        input.setGstRate(180.0);
        input.setEffectiveFrom(LocalDate.of(2025, 9, 22));

        assertThrows(GstRateAdminService.RateReviewException.class, () -> service.create(input));
    }

    @Test
    void supersede_shouldRejectAnEndDateBeforeTheStart() {
        GstRateMaster r = rate(1L, "1006", 5.0, GstRateMaster.STATUS_VERIFIED);
        when(rateRepo.findById(1L)).thenReturn(Optional.of(r));

        assertThrows(GstRateAdminService.RateReviewException.class,
                () -> service.supersede(1L, LocalDate.of(2020, 1, 1)));
    }

    @Test
    void listForReview_shouldExplainWhyASingleValueBandCannotBeApproved() {
        GstRateMaster lone = rate(1L, "64", 5.0, GstRateMaster.STATUS_UNVERIFIED);
        lone.setConditionType(GstRateMaster.CONDITION_VALUE_UPTO);
        lone.setThresholdAmount(2500.0);
        when(rateRepo.findByStatusOrderByHsnCodeAsc(GstRateMaster.STATUS_UNVERIFIED))
                .thenReturn(List.of(lone));

        var review = service.listForReview(null);

        assertEquals(1, review.size());
        assertEquals("64", review.get(0).get("hsnCode"));
        assertTrue(((String) review.get(0).get("reviewNote")).contains("Only one side"));
    }

    @Test
    void listForReview_shouldGroupEveryRateOfAHeadingTogether() {
        // The reason a row needs review is its siblings, so they have to arrive together.
        when(rateRepo.findByStatusOrderByHsnCodeAsc(GstRateMaster.STATUS_UNVERIFIED))
                .thenReturn(List.of(
                        rate(1L, "1006", 5.0, GstRateMaster.STATUS_UNVERIFIED),
                        rate(2L, "1006", 0.0, GstRateMaster.STATUS_UNVERIFIED)));

        var review = service.listForReview(null);

        assertEquals(1, review.size(), "one heading, not two separate rows");
        assertEquals(2, ((List<?>) review.get(0).get("rates")).size());
        assertTrue(((String) review.get(0).get("reviewNote")).contains("Several flat rates"));
    }
}
