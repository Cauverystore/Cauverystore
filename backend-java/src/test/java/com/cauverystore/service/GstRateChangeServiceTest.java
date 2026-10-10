package com.cauverystore.service;

import com.cauverystore.entities.GstRateMaster;
import com.cauverystore.entities.GstRateProposal;
import com.cauverystore.entities.GstRateSource;
import com.cauverystore.repository.GstRateMasterRepository;
import com.cauverystore.repository.GstRateProposalRepository;
import com.cauverystore.repository.GstRateSourceRepository;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * From a published notification to a rate that can be charged - and the point between the two
 * where a person has to say yes.
 */
class GstRateChangeServiceTest {

    private final GstRateProposalRepository proposalRepo = mock(GstRateProposalRepository.class);
    private final GstRateMasterRepository rateRepo = mock(GstRateMasterRepository.class);
    private final GstRateSourceRepository sourceRepo = mock(GstRateSourceRepository.class);
    private final ProductRepository productRepo = mock(ProductRepository.class);
    private final UserRepository userRepo = mock(UserRepository.class);
    private final EmailService emailService = mock(EmailService.class);
    private final GstRateChangeService service = new GstRateChangeService(proposalRepo, rateRepo,
            sourceRepo, productRepo, userRepo, new CbicNotificationReader(), emailService);

    private GstRateSource source(long id, String number) {
        GstRateSource s = mock(GstRateSource.class);
        when(s.getId()).thenReturn(id);
        when(s.getNotificationNumber()).thenReturn(number);
        return s;
    }

    private byte[] committed(String fileName) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/master-data/cbic-source/" + fileName)) {
            return in.readAllBytes();
        }
    }

    private GstRateMaster rate(String hsn, double pct, LocalDate from) {
        GstRateMaster r = new GstRateMaster();
        r.setHsnCode(hsn);
        r.setGstRate(pct);
        r.setEffectiveFrom(from);
        r.setStatus(GstRateMaster.STATUS_VERIFIED);
        return r;
    }

    private GstRateProposal pending(String hsn, Double pct, boolean attention) {
        GstRateProposal p = new GstRateProposal();
        p.setId(5L);
        p.setSourceId(9L);
        p.setNotificationNumber("01/2026-Central Tax (Rate)");
        p.setOperation("SUBSTITUTE_CODES");
        p.setHsnCode(hsn);
        p.setProposedRate(pct);
        p.setEffectiveFrom(LocalDate.of(2026, 5, 1));
        p.setNeedsAttention(attention);
        when(proposalRepo.findById(5L)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    @SuppressWarnings("unchecked")
    void draftsOneProposalPerCodeFromARealNotification() throws Exception {
        when(proposalRepo.existsBySourceId(9L)).thenReturn(false);
        when(rateRepo.findApplicable(eq("2202"), anyString(), any()))
                .thenReturn(List.of(rate("2202", 18.0, LocalDate.of(2025, 9, 22))));

        int drafted = service.draft(source(9L, "01/2026-Central Tax (Rate)"), committed("CTR-E-updated.pdf"));

        // Two codes each against S. Nos. 150 and 151, then three and two in Schedule III.
        assertEquals(9, drafted);
        ArgumentCaptor<List<GstRateProposal>> saved = ArgumentCaptor.forClass(List.class);
        verify(proposalRepo).saveAll(saved.capture());
        GstRateProposal first = saved.getValue().get(0);
        assertEquals("22029921", first.getHsnCode());
        assertEquals(5.0, first.getProposedRate());
        assertEquals(LocalDate.of(2026, 5, 1), first.getEffectiveFrom());
        // No row of its own, so today's rate comes from the four-digit heading above it.
        assertEquals(18.0, first.getRateWhenDrafted());
        assertEquals(GstRateProposal.STATUS_PENDING, first.getStatus());
        // Drafting never writes a rate.
        verify(rateRepo, never()).save(any());
    }

    @Test
    void doesNotDraftTheSameNotificationTwice() throws Exception {
        when(proposalRepo.existsBySourceId(9L)).thenReturn(true);

        assertEquals(0, service.draft(source(9L, "01/2026-Central Tax (Rate)"), committed("CTR-E-updated.pdf")));
        verify(proposalRepo, never()).saveAll(any());
    }

    @Test
    void acceptingClosesOffTheOldRateAndVerifiesTheNew() {
        pending("22029921", 5.0, false);
        GstRateMaster old = rate("22029921", 18.0, LocalDate.of(2025, 9, 22));
        when(rateRepo.findByHsnCodeOrderByEffectiveFromDesc("22029921")).thenReturn(List.of(old));
        when(proposalRepo.countBySourceIdAndStatus(anyLong(), anyString())).thenReturn(3L);

        GstRateProposal done = service.accept(5L, "priya@example.com", null);

        assertEquals(GstRateProposal.STATUS_ACCEPTED, done.getStatus());
        assertEquals("priya@example.com", done.getDecidedBy());
        // Last month's invoices must still resolve to last month's rate.
        assertEquals(LocalDate.of(2026, 4, 30), old.getEffectiveTo());
        ArgumentCaptor<GstRateMaster> written = ArgumentCaptor.forClass(GstRateMaster.class);
        verify(rateRepo, times(2)).save(written.capture());
        GstRateMaster fresh = written.getAllValues().get(1);
        assertEquals(5.0, fresh.getGstRate());
        assertEquals(LocalDate.of(2026, 5, 1), fresh.getEffectiveFrom());
        assertEquals(GstRateMaster.STATUS_VERIFIED, fresh.getStatus());
        assertEquals("priya@example.com", fresh.getVerifiedBy());
    }

    @Test
    void acceptingARateAlreadyInForceChangesNothing() {
        // The notification was applied by hand before the reader existed.
        pending("22029921", 5.0, false);
        when(rateRepo.findByHsnCodeOrderByEffectiveFromDesc("22029921"))
                .thenReturn(List.of(rate("22029921", 5.0, LocalDate.of(2026, 5, 1))));
        when(proposalRepo.countBySourceIdAndStatus(anyLong(), anyString())).thenReturn(1L);

        GstRateProposal done = service.accept(5L, "priya@example.com", null);

        assertEquals(GstRateProposal.STATUS_ACCEPTED, done.getStatus());
        verify(rateRepo, never()).save(any());
    }

    @Test
    void aClauseWithNoRateCannotBeAccepted() {
        pending(null, null, true);

        assertThrows(GstRateChangeService.RateChangeException.class,
                () -> service.accept(5L, "priya@example.com", null));
        verify(rateRepo, never()).save(any());
    }

    @Test
    void acceptingHasToBeAttributed() {
        pending("22029921", 5.0, false);

        assertThrows(GstRateChangeService.RateChangeException.class, () -> service.accept(5L, " ", null));
    }

    @Test
    void rejectingNeedsAReason() {
        pending("22029921", 5.0, false);

        assertThrows(GstRateChangeService.RateChangeException.class,
                () -> service.reject(5L, "priya@example.com", ""));
    }

    @Test
    void bulkAcceptLeavesDoubtfulRowsOnTheDesk() {
        GstRateProposal doubtful = pending("240319", 18.0, true);
        when(proposalRepo.findBySourceIdOrderByIdAsc(9L)).thenReturn(List.of(doubtful));

        assertEquals(0, service.acceptClean(9L, "priya@example.com").get("accepted"));
        assertEquals(GstRateProposal.STATUS_PENDING, doubtful.getStatus());
    }

    @Test
    void theNotificationCountsAsAppliedOnceNothingIsLeftUndecided() {
        pending("22029921", 5.0, false);
        when(rateRepo.findByHsnCodeOrderByEffectiveFromDesc("22029921")).thenReturn(List.of());
        when(proposalRepo.countBySourceIdAndStatus(9L, GstRateProposal.STATUS_PENDING)).thenReturn(0L);
        GstRateSource src = new GstRateSource("01/2026-Central Tax (Rate)", LocalDate.of(2026, 4, 30), null, "x");
        src.setApplied(false);
        when(sourceRepo.findById(9L)).thenReturn(Optional.of(src));

        service.accept(5L, "priya@example.com", null);

        assertTrue(src.getApplied());
        verify(sourceRepo).save(src);
    }
}
