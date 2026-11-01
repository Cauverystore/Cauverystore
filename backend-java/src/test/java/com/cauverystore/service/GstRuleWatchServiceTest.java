package com.cauverystore.service;

import com.cauverystore.entities.GstRuleUpdate;
import com.cauverystore.entities.Role;
import com.cauverystore.entities.User;
import com.cauverystore.repository.GstRuleUpdateRepository;
import com.cauverystore.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * The rule watcher must notice every non-rate notification and circular once, tell the admins
 * once per run, and never confuse "could not read CBIC" with "nothing new".
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GstRuleWatchServiceTest {

    @Mock private GstRuleUpdateRepository repo;
    @Mock private UserRepository userRepo;
    @Mock private EmailService emailService;

    private GstRuleWatchService service;

    private static final LocalDate SINCE = LocalDate.of(2026, 8, 1);

    private static final String NOTIFICATIONS = "["
            + "{\"id\":1010781,\"isActive\":\"Y\",\"notificationNo\":\"04/2026-Central Tax\","
            + "\"notificationName\":\"Seeks to notify category of supplies for purpose of e-invoicing.\","
            + "\"notificationCategory\":\"Central Tax\",\"notificationDt\":\"2026-10-26T05:30:00+05:30\"},"
            + "{\"id\":1010680,\"isActive\":\"Y\",\"notificationNo\":\"02/2026-Central Tax\","
            + "\"notificationName\":\"Old\",\"notificationCategory\":\"Central Tax\","
            + "\"notificationDt\":\"2026-05-07T05:30:00+05:30\"},"
            + "{\"id\":1010999,\"isActive\":\"N\",\"notificationNo\":\"99/2026-Central Tax\","
            + "\"notificationName\":\"Withdrawn\",\"notificationCategory\":\"Central Tax\","
            + "\"notificationDt\":\"2026-10-26T05:30:00+05:30\"}]";

    private static final String CIRCULARS = "["
            + "{\"id\":1003348,\"isActive\":\"Y\",\"circularNo\":\"257/03/2026-GST\","
            + "\"circularName\":\"Guidelines for the handling of applications for GST registration.\","
            + "\"circularCategory\":\"Circulars CGST\",\"circularDt\":\"2026-10-26T05:30:00+05:30\"}]";

    /** Shaped like gst.gov.in/fomessage/newsupdates on 2026-11-01. */
    private static final String ADVISORIES = "{\"data\":["
            + "{\"id\":678,\"title\":\"Introduction of e-Invoicing Facility for RCMDOM and RCMIMP\","
            + "\"module\":\"e-Invoice\",\"content\":\"<p>Dear Taxpayers,</p>\\n<p> Turnover of &#8377;5 crore"
            + " and above.</p><table><tr><th>Supply Type</th><th>Description</th></tr>"
            + "<tr><td><b>RCMDOM</b></td><td>Self-invoices</td></tr></table>\","
            + "\"date\":\"16/10/2026\",\"IsExternal\":\"N\",\"linkURl\":\"\"},"
            + "{\"id\":675,\"title\":\"Recommendations of the 57th GST Council Meeting\",\"module\":\"OTHERS \","
            + "\"content\":\"<p>Click here:<a href=\\\"https://tutorial.gst.gov.in/downloads/news/press_release.pdf\\\""
            + " target=\\\"blank\\\">link</a></p>\",\"date\":\"13/10/2026\",\"linkURl\":\"\"},"
            + "{\"id\":500,\"title\":\"Old\",\"module\":\"Returns\",\"content\":\"x\",\"date\":\"01/01/2025\"}]}";

    @BeforeEach
    void setUp() {
        service = spy(new GstRuleWatchService(repo, userRepo, emailService));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        User admin = new User();
        admin.setEmail("admin@example.test");
        admin.setActive(true);
        when(userRepo.findByRole(Role.ADMIN)).thenReturn(List.of(admin));
        when(userRepo.findByRole(Role.SUPER_ADMIN)).thenReturn(List.of());
    }

    private void stubSources() {
        GstRuleWatchService.Item eInvoicing = service.parseList(GstRuleUpdate.KIND_NOTIFICATION,
                "Central Tax", NOTIFICATIONS, SINCE).get(0);
        doReturn(List.of(eInvoicing)).when(service).fetchNotifications(eq("Central Tax"), any());
        doReturn(List.of()).when(service).fetchNotifications(
                argThat(c -> !"Central Tax".equals(c)), any());
        doReturn(service.parseList(GstRuleUpdate.KIND_CIRCULAR, "Circulars", CIRCULARS, SINCE))
                .when(service).fetchCirculars(any());
        doReturn(List.of()).when(service).fetchAdvisories(any());
    }

    @Test
    void shouldReadGstnAdvisories() {
        List<GstRuleWatchService.Item> got = service.parseAdvisories(ADVISORIES, SINCE);

        assertEquals(2, got.size(), "the 2025 advisory is outside the window");
        GstRuleWatchService.Item e = got.get(0);
        assertEquals(GstRuleUpdate.KIND_ADVISORY, e.kind());
        assertEquals("GSTN advisory 678", e.number());
        assertEquals("GSTN - e-Invoice", e.category());
        assertEquals(LocalDate.of(2026, 10, 16), e.issuedOn());
        assertEquals(678L, e.documentId());
        assertTrue(e.content().contains("Rs 5 crore"), e.content());
        assertTrue(e.content().contains("RCMDOM Self-invoices"), e.content());
        assertFalse(e.content().contains("<"), "no HTML left");
        assertNull(e.documentPath());
        assertEquals("https://tutorial.gst.gov.in/downloads/news/press_release.pdf", got.get(1).documentPath());
        assertEquals("GSTN - OTHERS", got.get(1).category());
    }

    @Test
    void shouldRefuseAnAdvisoryReplyWithoutAList() {
        assertThrows(IllegalStateException.class, () -> service.parseAdvisories("<html>Request Rejected</html>", SINCE));
        assertThrows(IllegalStateException.class, () -> service.parseAdvisories("{\"error\":\"x\"}", SINCE));
    }

    @Test
    void shouldRecordAnAdvisoryWithItsText() {
        stubSources();
        doReturn(service.parseAdvisories(ADVISORIES, SINCE).subList(0, 1)).when(service).fetchAdvisories(any());
        when(repo.existsByKindAndCbicDocumentId(any(), any())).thenReturn(false);

        service.check();

        ArgumentCaptor<GstRuleUpdate> saved = ArgumentCaptor.forClass(GstRuleUpdate.class);
        verify(repo, times(3)).save(saved.capture());
        GstRuleUpdate advisory = saved.getAllValues().stream()
                .filter(u -> GstRuleUpdate.KIND_ADVISORY.equals(u.getKind())).findFirst().orElseThrow();
        assertEquals(678L, advisory.getCbicDocumentId());
        assertTrue(advisory.getContent().startsWith("Dear Taxpayers,"));
    }

    @Test
    void shouldKeepRecentActiveNotifications() {
        List<GstRuleWatchService.Item> got = service.parseList(
                GstRuleUpdate.KIND_NOTIFICATION, "Central Tax", NOTIFICATIONS, SINCE);

        assertEquals(1, got.size(), "older and withdrawn entries are left out");
        assertEquals("04/2026-Central Tax", got.get(0).number());
        assertEquals(LocalDate.of(2026, 10, 26), got.get(0).issuedOn());
        assertEquals(1010781L, got.get(0).documentId());
    }

    @Test
    void shouldReadCircularFieldNames() {
        List<GstRuleWatchService.Item> got = service.parseList(
                GstRuleUpdate.KIND_CIRCULAR, "Circulars", CIRCULARS, SINCE);

        assertEquals(1, got.size());
        assertEquals("257/03/2026-GST", got.get(0).number());
        assertEquals("Circulars CGST", got.get(0).category());
    }

    @Test
    void shouldRefuseSomethingThatIsNotAList() {
        assertThrows(IllegalStateException.class, () -> service.parseList(
                GstRuleUpdate.KIND_CIRCULAR, "Circulars", "<html></html>", SINCE));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRecordNewItemsAndEmailTheAdminsOnce() {
        stubSources();
        when(repo.existsByKindAndCbicDocumentId(any(), any())).thenReturn(false);

        Map<String, Object> result = service.check();

        assertEquals(2, result.get("found"));
        verify(repo, times(2)).save(any(GstRuleUpdate.class));
        ArgumentCaptor<List<GstRuleUpdate>> sent = ArgumentCaptor.forClass(List.class);
        verify(emailService, times(1)).sendGstRuleUpdatesDigest(eq("admin@example.test"), sent.capture());
        assertEquals(2, sent.getValue().size(), "one digest, not one email per item");
        assertTrue(sent.getValue().stream().allMatch(u -> GstRuleUpdate.STATUS_NEW.equals(u.getStatus())));
    }

    @Test
    void shouldNotRecordOrEmailWhatIsAlreadyKnown() {
        stubSources();
        when(repo.existsByKindAndCbicDocumentId(any(), any())).thenReturn(true);

        Map<String, Object> result = service.check();

        assertEquals(0, result.get("found"));
        verify(repo, never()).save(any());
        verify(emailService, never()).sendGstRuleUpdatesDigest(any(), any());
    }

    @Test
    void shouldReportBlindWhenNoSourceCanBeRead() {
        doThrow(new IllegalStateException("moved")).when(service).fetchNotifications(any(), any());
        doThrow(new IllegalStateException("moved")).when(service).fetchCirculars(any());
        doThrow(new IllegalStateException("Request Rejected")).when(service).fetchAdvisories(any());

        Map<String, Object> result = service.check();

        assertEquals(false, result.get("reachable"));
        assertEquals(GstRuleWatchService.RULE_CATEGORIES.size() + 2,
                ((List<?>) result.get("problems")).size());
    }

    @Test
    void shouldKeepGoingWhenOneSourceFails() {
        stubSources();
        doThrow(new IllegalStateException("moved")).when(service).fetchCirculars(any());
        when(repo.existsByKindAndCbicDocumentId(any(), any())).thenReturn(false);

        Map<String, Object> result = service.check();

        assertEquals(true, result.get("reachable"));
        assertEquals(1, result.get("found"), "the notification is still recorded");
    }

    @Test
    void scheduledCheckShouldNeverThrow() {
        doThrow(new RuntimeException("boom")).when(service).check();
        assertDoesNotThrow(service::scheduledCheck);
    }

    @Test
    void actionNeededShouldSayWhatToDo() {
        GstRuleUpdate u = new GstRuleUpdate();
        when(repo.findById(5L)).thenReturn(Optional.of(u));

        assertThrows(IllegalArgumentException.class,
                () -> service.review(5L, GstRuleUpdate.STATUS_ACTION_NEEDED, " ", "a@b"));
        assertThrows(IllegalArgumentException.class,
                () -> service.review(5L, "DONE", null, "a@b"));

        GstRuleUpdate saved = service.review(5L, GstRuleUpdate.STATUS_REVIEWED, "Not our turnover band", "a@b");
        assertEquals(GstRuleUpdate.STATUS_REVIEWED, saved.getStatus());
        assertEquals("a@b", saved.getReviewedBy());
        assertNotNull(saved.getReviewedAt());

        GstRuleUpdate reopened = service.review(5L, GstRuleUpdate.STATUS_NEW, null, "a@b");
        assertNull(reopened.getReviewedBy(), "reopening clears the sign-off");
    }

    @Test
    @org.junit.jupiter.api.Tag("live")
    void live_shouldReadTheRealLists() {
        GstRuleWatchService real = new GstRuleWatchService(repo, userRepo, emailService);
        List<GstRuleWatchService.Item> notes;
        List<GstRuleWatchService.Item> circulars;
        try {
            notes = real.fetchNotifications("Central Tax", LocalDate.now().minusYears(2));
            circulars = real.fetchCirculars(LocalDate.now().minusYears(2));
        } catch (Exception e) {
            org.junit.jupiter.api.Assumptions.abort("CBIC not reachable from this JVM ("
                    + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
            return;
        }
        assertFalse(notes.isEmpty(), "Central Tax notifications from the last two years");
        assertFalse(circulars.isEmpty(), "GST circulars from the last two years");
        assertTrue(circulars.stream().allMatch(c -> c.documentId() != null && c.number() != null));
        List<GstRuleWatchService.Item> advisories = real.fetchAdvisories(LocalDate.now().minusYears(2));
        assertFalse(advisories.isEmpty(), "GSTN advisories from the last two years");
        assertTrue(advisories.stream().allMatch(a -> a.documentId() != null && a.content() != null));
        System.out.println("  GSTN advisories, last 2 years: " + advisories.size());
        System.out.println("  CBIC rule items, last 2 years: " + notes.size() + " notifications, "
                + circulars.size() + " circulars");
    }
}
