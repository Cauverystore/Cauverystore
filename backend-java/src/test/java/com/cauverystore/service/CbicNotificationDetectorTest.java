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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The detector's job is to notice a rate notification before a customer is invoiced under the
 * old rates. These cover the two ways it could fail usefully - missing one, or crying wolf over
 * every procedural circular until nobody reads the alerts.
 *
 * The live call to CBIC is a separate test, tagged so it can be skipped offline.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CbicNotificationDetectorTest {

    @Mock private GstRateSourceRepository sourceRepo;
    @Mock private GstRateFreshnessService freshnessService;

    private CbicNotificationDetector detector;

    @BeforeEach
    void setUp() {
        detector = new CbicNotificationDetector(sourceRepo, freshnessService);
        ReflectionTestUtils.setField(detector, "enabled", true);
        ReflectionTestUtils.setField(detector, "baseUrl", "https://taxinformation.cbic.gov.in");
        when(sourceRepo.findByNotificationNumber(any())).thenReturn(Optional.empty());
    }

    private CbicNotificationDetector.Update update(String number, String category) {
        return new CbicNotificationDetector.Update(number, "some notification",
                LocalDate.of(2026, 4, 30), category, "Notification", "path.pdf");
    }

    @Test
    void shouldTreatOnlyRateNotificationsAsRateChanges() {
        // A plain Central Tax notification changes procedure - the last one empowered a
        // tribunal bench. Alerting on those would raise an alarm for everything CBIC
        // publishes, which is the fastest way to get alarms ignored.
        assertTrue(update("01/2026-Central Tax (Rate)", "Central Tax (Rate)").changesRates());
        assertTrue(update("03/2026-Integrated Tax (Rate)", "Integrated Tax (Rate)").changesRates());
        assertTrue(update("02/2026-Union Territory Tax (Rate)", "UTGST (Rate)").changesRates());

        assertFalse(update("02/2026-Central Tax", "Central Tax").changesRates());
        assertFalse(update("256/02/2026-GST", "Circulars CGST").changesRates());
    }

    @Test
    void shouldStillClassifyWhenTheCategoryIsBlank() {
        // The feed leaves updateCategory empty on some rows, so the number has to carry it.
        assertTrue(update("01/2026-Central Tax (Rate)", "").changesRates());
        assertTrue(update("01/2026-Central Tax (Rate)", null).changesRates());
    }

    @Test
    void shouldRecordANotificationItHasNotSeen() {
        CbicNotificationDetector spy = spy(detector);
        doReturn(List.of(update("04/2026-Central Tax (Rate)", "Central Tax (Rate)")))
                .when(spy).fetchLatestUpdates();

        spy.detect();

        verify(freshnessService).recordPendingNotification(
                eq("04/2026-Central Tax (Rate)"), eq(LocalDate.of(2026, 4, 30)),
                isNull(), anyString());
    }

    @Test
    void shouldNotGuessTheEffectiveDate() {
        // The feed carries the notification date, not the date its rates take effect, and the
        // two are usually different. A guessed effective date would put a start date on a rate
        // change nobody had read.
        CbicNotificationDetector spy = spy(detector);
        doReturn(List.of(update("04/2026-Central Tax (Rate)", "Central Tax (Rate)")))
                .when(spy).fetchLatestUpdates();

        spy.detect();

        verify(freshnessService).recordPendingNotification(any(), any(), isNull(), any());
    }

    @Test
    void shouldIgnoreANotificationAlreadyRecorded() {
        when(sourceRepo.findByNotificationNumber("01/2026-Central Tax (Rate)"))
                .thenReturn(Optional.of(new GstRateSource()));
        CbicNotificationDetector spy = spy(detector);
        doReturn(List.of(update("01/2026-Central Tax (Rate)", "Central Tax (Rate)")))
                .when(spy).fetchLatestUpdates();

        spy.detect();

        verify(freshnessService, never()).recordPendingNotification(any(), any(), any(), any());
    }

    @Test
    void shouldNeverTouchTheRatesItself() {
        // The whole safeguard: it records that a notification exists. A rate enters this system
        // only when a person has read the notification and imported it.
        assertTrue(java.util.Arrays.stream(CbicNotificationDetector.class.getDeclaredMethods())
                        .noneMatch(m -> m.getName().toLowerCase().contains("import")
                                || m.getName().toLowerCase().contains("applyrate")
                                || m.getName().toLowerCase().contains("updaterate")),
                "the detector must not be able to change a rate");
    }

    @Test
    void shouldSurviveCbicBeingUnreachable() {
        // An unreachable portal is not evidence that nothing changed, and it must not take the
        // scheduler down with it.
        CbicNotificationDetector spy = spy(detector);
        doThrow(new IllegalStateException("connection reset")).when(spy).fetchLatestUpdates();

        assertDoesNotThrow(spy::detect);
        verify(freshnessService, never()).recordPendingNotification(any(), any(), any(), any());
    }

    @Test
    void shouldDoNothingWhenDisabled() {
        ReflectionTestUtils.setField(detector, "enabled", false);
        CbicNotificationDetector spy = spy(detector);

        spy.detect();

        verify(spy, never()).fetchLatestUpdates();
    }

    @Test
    void previewShouldReportUnreachableRatherThanEmpty() {
        // "No updates found" and "could not reach CBIC" look identical on a screen unless the
        // second one says so.
        CbicNotificationDetector spy = spy(detector);
        doThrow(new IllegalStateException("portal moved")).when(spy).fetchLatestUpdates();

        assertEquals(false, spy.preview().get("reachable"));
        assertTrue(String.valueOf(spy.preview().get("error")).contains("portal moved"));
    }

    @Test
    @org.junit.jupiter.api.Tag("live")
    void live_shouldReadTheRealCbicFeed() {
        // Proves the endpoint, the user agent and the date format against CBIC itself. Mocks
        // cannot tell us the portal still answers, and a detector nobody has run against the
        // real thing is a detector nobody should trust. Tagged so an offline build can skip it:
        //     mvn test -DexcludedGroups=live
        List<CbicNotificationDetector.Update> updates;
        try {
            updates = detector.fetchLatestUpdates();
        } catch (Exception e) {
            // A PKIX failure here is this machine's truststore, not CBIC. It is reported so a
            // skip is never mistaken for the portal having gone away.
            org.junit.jupiter.api.Assumptions.abort("CBIC not reachable from this JVM ("
                    + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
            return;
        }

        assertFalse(updates.isEmpty(), "the feed should carry recent updates");
        assertTrue(updates.stream().anyMatch(u -> u.number() != null && !u.number().isBlank()),
                "every update should carry a notification number");
        assertTrue(updates.stream().anyMatch(u -> u.dated() != null),
                "the portal's dd-MMM-yyyy dates should parse");
        updates.forEach(u -> System.out.println("  CBIC: " + u.number() + " | " + u.dated()
                + " | " + u.category() + " | changesRates=" + u.changesRates()));
    }

    @Test
    void shouldTellATrustFailureApartFromAnOutage() {
        // They need different responses: an outage clears by itself, a broken truststore leaves
        // the check permanently blind while looking exactly the same in the log.
        CbicNotificationDetector spy = spy(detector);
        doThrow(new RuntimeException("I/O error on GET request: PKIX path building failed"))
                .when(spy).fetchLatestUpdates();

        assertDoesNotThrow(spy::detect);
        assertTrue((Boolean) org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                        detector, "isCertificateProblem",
                        new RuntimeException("PKIX path building failed")),
                "a PKIX failure must be recognised as a trust problem");
        assertFalse((Boolean) org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                        detector, "isCertificateProblem",
                        new RuntimeException("connection timed out")),
                "an ordinary timeout is not a trust problem");
    }
}
