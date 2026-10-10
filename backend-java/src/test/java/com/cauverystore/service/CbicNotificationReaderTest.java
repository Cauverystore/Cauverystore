package com.cauverystore.service;

import com.cauverystore.service.CbicNotificationReader.Change;
import com.cauverystore.service.CbicNotificationReader.Operation;
import com.cauverystore.service.CbicNotificationReader.Reading;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reading real CBIC notifications by rule.
 *
 * Run against the notifications committed under master-data/cbic-source, because those are the
 * documents the rates in force were actually taken from by hand - so what the reader finds can
 * be held against what a person found.
 */
class CbicNotificationReaderTest {

    private final CbicNotificationReader reader = new CbicNotificationReader();

    private Reading readCommitted(String fileName) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/master-data/cbic-source/" + fileName)) {
            assertNotNull(in, fileName + " should be on the classpath");
            return reader.readPdf(in.readAllBytes());
        }
    }

    private List<Change> of(Reading r, String schedule, Operation op) {
        return r.changes().stream()
                .filter(c -> schedule.equals(c.schedule()) && c.operation() == op).toList();
    }

    @Test
    void readsTheBeverageRecutOfMay2026() throws Exception {
        Reading r = readCommitted("CTR-E-updated.pdf");

        assertEquals("01/2026-Central Tax (Rate)", r.notificationNumber());
        assertEquals(LocalDate.of(2026, 5, 1), r.effectiveFrom());

        // Schedule I is 2.5% CGST, which is 5% to the customer.
        List<Change> five = of(r, "I", Operation.SUBSTITUTE_CODES);
        assertEquals(2, five.size());
        assertEquals(5.0, five.get(0).totalRate());
        assertEquals(List.of("22029921", "22029929"), five.get(0).codes());
        assertEquals("150", five.get(0).serialNo());
        assertEquals(List.of("22029931", "22029939"), five.get(1).codes());

        // Schedule III is 20% CGST, which is 40%.
        List<Change> forty = of(r, "III", Operation.SUBSTITUTE_CODES);
        assertEquals(2, forty.size());
        assertEquals(40.0, forty.get(0).totalRate());
        assertEquals(List.of("22029100", "22029991", "22029999"), forty.get(0).codes());
        assertEquals(List.of("22029991", "22029999"), forty.get(1).codes());

        assertTrue(r.changes().stream().noneMatch(c -> c.operation() == Operation.UNREAD),
                "every clause of this notification fits a known formula");
    }

    @Test
    void readsTheTobaccoChangesOfFebruary2026() throws Exception {
        Reading r = readCommitted("19-2025-CTR-Eng.pdf");

        assertEquals("19/2025-Central Tax (Rate)", r.notificationNumber());
        assertEquals(LocalDate.of(2026, 2, 1), r.effectiveFrom());

        // Biris are inserted into Schedule II: 9% CGST, 18% to the customer.
        List<Change> eighteen = of(r, "II", Operation.INSERT);
        assertEquals(1, eighteen.size());
        assertEquals(18.0, eighteen.get(0).totalRate());
        assertEquals("4A", eighteen.get(0).serialNo());
        assertTrue(eighteen.get(0).codes().contains("24031921"));

        // Pan masala and tobacco are inserted into Schedule III at 40%.
        List<Change> forty = of(r, "III", Operation.INSERT);
        assertEquals(6, forty.size());
        assertTrue(forty.stream().allMatch(c -> c.totalRate() == 40.0));
        assertEquals(List.of("21069020"), forty.get(0).codes());
        assertTrue(forty.get(0).description().toLowerCase().contains("pan masala"));
        assertTrue(forty.stream().anyMatch(c -> c.codes().contains("24041100")));

        // Schedule VII goes entirely, and the notification does not say what was in it.
        List<Change> omitted = of(r, "VII", Operation.OMIT);
        assertEquals(1, omitted.size());
        assertTrue(omitted.get(0).needsAttention());
    }

    @Test
    void doesNotCallACarveOutClean() throws Exception {
        // "2403 (other than 2403 19 21, 2403 19 29)" - 2403 does not all go to 40%.
        Reading r = readCommitted("19-2025-CTR-Eng.pdf");
        Change tobacco = of(r, "III", Operation.INSERT).stream()
                .filter(c -> "17".equals(c.serialNo())).findFirst().orElseThrow();
        assertTrue(tobacco.needsAttention());
    }

    @Test
    void integratedTaxSchedulesStateTheWholeRate() {
        Reading r = reader.read("Notification No. 01/2026-Integrated Tax (Rate) ... In the said "
                + "notification, - (a) in Schedule I – 5%, (i) against S. No. 150, for the entry in "
                + "column (2), the entry \"2202 99 21\" shall be substituted; 2. This notification "
                + "shall come into force from 1st May, 2026.");
        assertEquals(5.0, r.changes().get(0).totalRate());
    }

    @Test
    void handsBackWhatItCannotRead() {
        Reading r = reader.read("Notification No. 05/2026-Central Tax (Rate) In the said "
                + "notification, - (a) in Schedule II – 9%, the Explanation below S. No. 12 shall "
                + "be read as if the words \"put up in unit containers\" were omitted; 2. This "
                + "notification shall come into force on the 1st day of July, 2026.");
        assertEquals(1, r.changes().size());
        assertEquals(Operation.UNREAD, r.changes().get(0).operation());
        assertFalse(r.readAnything());
    }

    @Test
    void aDocumentThatIsNotAnAmendmentIsSaidToBeOne() {
        Reading r = reader.read("Notification No. 02/2026-Central Tax ... the Principal Bench shall "
                + "hear appeals under section 101B.");
        assertTrue(r.changes().isEmpty());
        assertFalse(r.warnings().isEmpty());
    }

    @Test
    void aSplitCodeIsNotTrusted() {
        // The second code lost its last pair to a wrapped line, leaving a valid-looking 6 digits.
        Reading r = reader.read("Notification No. 19/2025-Central Tax (Rate) In the said notification, - "
                + "(a) in the Schedule II – 9%, after S. No. 4 and the entries relating thereto, the "
                + "following serial number and entries shall be inserted, namely: - \"4A. 2403 19 21, "
                + "2403 19 Biris;\"; 29 2. This notification shall come into force on the 1st day of "
                + "February, 2026.");
        Change biris = r.changes().get(0);
        assertEquals(Operation.INSERT, biris.operation());
        assertTrue(biris.needsAttention(), "a six-digit code beside an eight-digit one is a split code");
    }
}
