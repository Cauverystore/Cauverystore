package com.cauverystore.service;

import com.cauverystore.entities.GstRateSource;
import com.cauverystore.repository.GstRateSourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

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
 * Covers the one question the rate table could not previously answer: are these still current?
 *
 * CBIC's notification API needs credentials, so this cannot be polled. What it does instead is
 * measure how old the last human check is and escalate - so the tests are mostly about the
 * escalation being honest, especially the difference between "checked a while ago" and "never
 * checked at all".
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GstRateFreshnessServiceTest {

    @Mock private GstRateSourceRepository sourceRepo;
    @Mock private AuditService auditService;

    private GstRateFreshnessService service;

    @BeforeEach
    void setUp() {
        service = new GstRateFreshnessService(sourceRepo, auditService);
        ReflectionTestUtils.setField(service, "dueAfterDays", 30);
        ReflectionTestUtils.setField(service, "overdueAfterDays", 60);
        when(sourceRepo.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        when(sourceRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private GstRateSource source(String number, LocalDate effective, LocalDateTime verifiedAt) {
        GstRateSource s = new GstRateSource(number, effective.minusDays(30), effective, "test");
        s.setLastVerifiedAt(verifiedAt);
        if (verifiedAt != null) s.setLastVerifiedBy("priya@cauverystore.in");
        return s;
    }

    @Test
    void shouldDistinguishNeverCheckedFromCheckedLongAgo() {
        // Collapsing these would hide a store nobody has ever verified behind what looks like a
        // routine reminder.
        when(sourceRepo.findAll()).thenReturn(List.of(
                source("09/2025-Central Tax (Rate)", LocalDate.of(2025, 9, 22), null)));

        assertEquals(GstRateFreshnessService.STATUS_NEVER, service.status().get("status"));
        assertTrue(String.valueOf(service.status().get("message")).contains("Nobody has confirmed"));
    }

    @Test
    void shouldReportCurrent_whenCheckedRecently() {
        when(sourceRepo.findAll()).thenReturn(List.of(
                source("01/2026-Central Tax (Rate)", LocalDate.of(2026, 5, 1),
                        LocalDateTime.now().minusDays(3))));

        Map<String, Object> status = service.status();

        assertEquals(GstRateFreshnessService.STATUS_CURRENT, status.get("status"));
        assertEquals(3L, status.get("daysSinceVerified"));
        assertEquals("priya@cauverystore.in", status.get("lastVerifiedBy"));
    }

    @Test
    void shouldEscalateAsTheCheckAges() {
        when(sourceRepo.findAll()).thenReturn(List.of(
                source("01/2026-Central Tax (Rate)", LocalDate.of(2026, 5, 1),
                        LocalDateTime.now().minusDays(35))));
        assertEquals(GstRateFreshnessService.STATUS_DUE, service.status().get("status"));

        when(sourceRepo.findAll()).thenReturn(List.of(
                source("01/2026-Central Tax (Rate)", LocalDate.of(2026, 5, 1),
                        LocalDateTime.now().minusDays(75))));
        Map<String, Object> overdue = service.status();
        assertEquals(GstRateFreshnessService.STATUS_OVERDUE, overdue.get("status"));
        assertTrue(String.valueOf(overdue.get("message")).contains("every invoice raised since"),
                "the message has to say what the risk actually is");
    }

    @Test
    void shouldReportTheNewestNotificationApplied_notTheFirst() {
        when(sourceRepo.findAll()).thenReturn(List.of(
                source("09/2025-Central Tax (Rate)", LocalDate.of(2025, 9, 22), LocalDateTime.now()),
                source("01/2026-Central Tax (Rate)", LocalDate.of(2026, 5, 1), LocalDateTime.now())));

        assertEquals("01/2026-Central Tax (Rate)", service.status().get("newestNotification"));
    }

    @Test
    void shouldStampEveryAppliedSource_soTheClaimCoversTheWholeSet() {
        // The claim being made is "these rates, as a set, were confirmed today" - stamping one
        // row would leave the others looking unchecked.
        List<GstRateSource> all = new ArrayList<>(List.of(
                source("09/2025-Central Tax (Rate)", LocalDate.of(2025, 9, 22), null),
                source("01/2026-Central Tax (Rate)", LocalDate.of(2026, 5, 1), null)));
        when(sourceRepo.findAll()).thenReturn(all);

        service.recordVerification("priya@cauverystore.in", "checked the CBIC notification list");

        assertTrue(all.stream().allMatch(s -> s.getLastVerifiedAt() != null));
        assertTrue(all.stream().allMatch(s -> "priya@cauverystore.in".equals(s.getLastVerifiedBy())));
        verify(auditService).log(any(), eq("priya@cauverystore.in"), eq("GST_RATES_VERIFIED"),
                any(), any(), any(), any());
    }

    @Test
    void shouldRefuseAnUnattributedVerification() {
        // An unattributed check is not evidence of anything.
        assertThrows(IllegalArgumentException.class, () -> service.recordVerification("  ", null));
        assertThrows(IllegalArgumentException.class, () -> service.recordVerification(null, null));
    }

    @Test
    void shouldRecordAFoundNotificationAsNotYetApplied() {
        // Marking it applied would claim the rate table already reflects it, which it does not -
        // applying it means regenerating the seed.
        when(sourceRepo.findAll()).thenReturn(List.of());
        when(sourceRepo.findByNotificationNumber("03/2026-Central Tax (Rate)"))
                .thenReturn(Optional.empty());

        GstRateSource pending = service.recordPendingNotification(
                "03/2026-Central Tax (Rate)", LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 8, 1), "Rate change on textiles");

        assertFalse(pending.getApplied());
    }

    @Test
    void shouldNotDuplicateANotificationAlreadyRecorded() {
        GstRateSource existing = source("03/2026-Central Tax (Rate)", LocalDate.of(2026, 8, 1), null);
        when(sourceRepo.findByNotificationNumber("03/2026-Central Tax (Rate)"))
                .thenReturn(Optional.of(existing));

        service.recordPendingNotification("03/2026-Central Tax (Rate)", null, null, null);

        verify(sourceRepo, never()).save(any());
    }

    @Test
    void seedingShouldNotResetAnExistingVerification() {
        // Re-seeding on every restart would quietly wipe the stamps and make a verified store
        // look as though nobody had ever checked.
        when(sourceRepo.findByNotificationNumber(any())).thenReturn(
                Optional.of(source("09/2025-Central Tax (Rate)", LocalDate.of(2025, 9, 22),
                        LocalDateTime.now())));

        service.seedKnownSources();

        verify(sourceRepo, never()).save(any());
    }

    @Test
    void theDailyChaseShouldNeverThrow() {
        // It runs unattended; a failure to report must not take the scheduler down with it.
        when(sourceRepo.findAll()).thenThrow(new RuntimeException("database down"));

        assertDoesNotThrow(() -> service.chaseVerification());
    }
}
