package com.cauverystore.service;

import com.cauverystore.entities.TcsRecord;
import com.cauverystore.repository.TcsRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * GSTR-8 is the one return the marketplace files in its own name, and it is filed by uploading a
 * file the portal validates strictly. These tests hold the two things that are easy to get wrong
 * and impossible to notice until the upload is rejected or a seller is short-paid.
 */
@ExtendWith(MockitoExtension.class)
class Gstr8ExportServiceTest {

    @Mock private TcsRecordRepository tcsRepo;

    private TcsRecord row(String gstin, String pos, boolean interState, boolean registered,
                          boolean reversal, double taxable, double tcs) {
        TcsRecord r = new TcsRecord();
        r.setSellerGstin(gstin);
        r.setPlaceOfSupplyState(pos);
        r.setInterState(interState);
        r.setCustomerRegistered(registered);
        r.setEntryType(reversal ? TcsRecord.ENTRY_REVERSAL : TcsRecord.ENTRY_COLLECTION);
        r.setTaxableAmount(taxable);
        r.setTcsAmount(tcs);
        r.setPeriod("082026");
        return r;
    }

    private Gstr8ExportService serviceWith(TcsRecord... rows) {
        when(tcsRepo.findAll()).thenReturn(new ArrayList<>(List.of(rows)));
        return new Gstr8ExportService(tcsRepo);
    }

    @Test
    void oneSupplierInTwoStatesIsTwoRowsNotOne() {
        // Table 3 is keyed on supplier GSTIN *and* place of supply. Summing a supplier's month
        // into a single row reads fine in our own reports and is rejected by the utility, which
        // is why the place of supply had to be recorded on the TCS ledger at all.
        Gstr8ExportService svc = serviceWith(
                row("33AAAAA0000A1Z5", "33", false, false, false, 1000, 5.0),
                row("33AAAAA0000A1Z5", "29", true, false, false, 2000, 10.0));

        List<Map<String, Object>> t3 = svc.table3("082026");

        assertEquals(2, t3.size());
    }

    @Test
    void intraStateTcsIsSplitInHalfAndInterStateIsAllIntegratedTax() {
        // The head follows the supply, not the seller. Putting intra-state TCS under integrated
        // tax credits the wrong government and leaves the seller's cash ledger unable to absorb
        // it. Halving is right because the 0.5% is the *total* - 0.25% each side.
        Gstr8ExportService svc = serviceWith(
                row("33AAAAA0000A1Z5", "33", false, false, false, 1000, 5.0),
                row("33AAAAA0000A1Z5", "29", true, false, false, 2000, 10.0));

        Map<String, Object> intra = svc.table3("082026").stream()
                .filter(r -> "33".equals(r.get("POS"))).findFirst().orElseThrow();
        Map<String, Object> inter = svc.table3("082026").stream()
                .filter(r -> "29".equals(r.get("POS"))).findFirst().orElseThrow();

        assertEquals(0.0, intra.get("Integrated tax"));
        assertEquals(2.5, intra.get("Central tax"));
        assertEquals(2.5, intra.get("State/UT tax"));
        assertEquals(5.0, intra.get("Amount of TCS"));

        assertEquals(10.0, inter.get("Integrated tax"));
        assertEquals(0.0, inter.get("Central tax"));
        assertEquals(0.0, inter.get("State/UT tax"));
    }

    @Test
    void aReturnReducesTheNetItDoesNotAddToIt() {
        // Section 52 charges TCS on the net of the month. Reversals are stored as negative rows,
        // so a sign slip here silently doubles what is paid over instead of cancelling it.
        Gstr8ExportService svc = serviceWith(
                row("33AAAAA0000A1Z5", "33", false, false, false, 1000, 5.0),
                row("33AAAAA0000A1Z5", "33", false, false, true, -400, -2.0));

        Map<String, Object> r = svc.table3("082026").get(0);

        assertEquals(1000.0, r.get("Gross value of supplies made to unregistered persons"));
        assertEquals(400.0, r.get("Value of supplies returned by unregistered persons"));
        assertEquals(600.0, r.get("Net amount liable for TCS"));
        assertEquals(3.0, r.get("Amount of TCS"));
    }

    @Test
    void registeredAndUnregisteredBuyersAreKeptInSeparateColumns() {
        Gstr8ExportService svc = serviceWith(
                row("33AAAAA0000A1Z5", "33", false, true, false, 1000, 5.0),
                row("33AAAAA0000A1Z5", "33", false, false, false, 500, 2.5));

        Map<String, Object> r = svc.table3("082026").get(0);

        assertEquals(1000.0, r.get("Gross value of supplies made to registered persons"));
        assertEquals(500.0, r.get("Gross value of supplies made to unregistered persons"));
    }

    @Test
    void aRowWithNoPlaceOfSupplyBlocksFilingInsteadOfBeingUploadedAndRejected() {
        // POS is mandatory in the utility. Rows invoiced before we recorded it exist, and the
        // useful behaviour is to say so here rather than let somebody find out at the portal.
        Gstr8ExportService svc = serviceWith(
                row("33AAAAA0000A1Z5", null, false, false, false, 1000, 5.0));

        Map<String, Object> out = svc.forPeriod("082026");

        assertEquals(Boolean.FALSE, out.get("readyToFile"));
        assertFalse(((List<?>) out.get("blockingIssues")).isEmpty());
    }

    @Test
    void anotherMonthsSuppliesAreNotPulledIntoThisReturn() {
        TcsRecord old = row("33AAAAA0000A1Z5", "33", false, false, false, 9999, 50.0);
        old.setPeriod("072026");
        Gstr8ExportService svc = serviceWith(
                row("33AAAAA0000A1Z5", "33", false, false, false, 1000, 5.0), old);

        assertEquals(1000.0,
                svc.table3("082026").get(0).get("Gross value of supplies made to unregistered persons"));
    }

    @Test
    void table3_1IsGeneratedEmptyRatherThanOmitted() {
        // Enrolment-number sellers are not onboarded. A nil table says the question was asked.
        Gstr8ExportService svc = new Gstr8ExportService(tcsRepo);
        assertTrue(svc.table3_1("082026").isEmpty());
        verifyNoInteractions(tcsRepo);
    }
}
