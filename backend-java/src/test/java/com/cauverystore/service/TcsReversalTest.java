package com.cauverystore.service;

import com.cauverystore.entities.GstConfiguration;
import com.cauverystore.entities.TcsRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the arithmetic and the guardrails around TCS reversals.
 *
 * Section 52 charges TCS on NET monthly supplies. Before this, a credit note computed a TCS
 * figure and put it on the note alone, so tcs_records went on reporting money that had been
 * refunded - and GSTR-8 is filed from that table.
 */
class TcsReversalTest {

    private TcsRecord collection(double amount, String period) {
        TcsRecord t = new TcsRecord();
        t.setEntryType(TcsRecord.ENTRY_COLLECTION);
        t.setTcsAmount(amount);
        t.setTaxableAmount(amount * 100);
        t.setPeriod(period);
        t.setTransactionDate(LocalDate.of(2026, 8, 1));
        return t;
    }

    private TcsRecord reversal(double amount, String period, Long reverses) {
        TcsRecord t = new TcsRecord();
        t.setEntryType(TcsRecord.ENTRY_REVERSAL);
        t.setTcsAmount(amount);
        t.setTaxableAmount(amount * 100);
        t.setPeriod(period);
        t.setReversesId(reverses);
        t.setTransactionDate(LocalDate.of(2026, 8, 20));
        return t;
    }

    @Test
    void summingAPeriodShouldNetReversalsAgainstCollections() {
        // The whole point: the figure filed is gross less returns, and it falls out of summing
        // the period only because reversals are stored negative.
        List<TcsRecord> period = List.of(
                collection(10.0, "082026"),
                collection(25.0, "082026"),
                reversal(-10.0, "082026", 1L));

        double net = period.stream().mapToDouble(TcsRecord::getTcsAmount).sum();

        assertEquals(25.0, net, 0.001);
    }

    @Test
    void aReversalShouldBeIdentifiableAndPointAtWhatItCancels() {
        TcsRecord r = reversal(-10.0, "082026", 42L);

        assertTrue(r.isReversal());
        assertEquals(42L, r.getReversesId(), "an unattributable reversal cannot be audited");
        assertTrue(r.getTcsAmount() < 0, "a positive reversal would add to the liability");
    }

    @Test
    void aCollectionShouldNotReadAsAReversal() {
        assertFalse(collection(10.0, "082026").isReversal());
        assertEquals(TcsRecord.ENTRY_COLLECTION, new TcsRecord().getEntryType(),
                "the default must be a collection, so existing rows keep their meaning");
    }

    @Test
    void aLateReturnShouldReverseInTheCurrentPeriod_notTheOriginal() {
        // A filed period is a statement about a date and is never recomputed, so a return
        // arriving afterwards lands in the month it actually happened.
        List<TcsRecord> july = List.of(collection(30.0, "072026"));
        List<TcsRecord> august = List.of(reversal(-30.0, "082026", 1L));

        assertEquals(30.0, july.stream().mapToDouble(TcsRecord::getTcsAmount).sum(), 0.001,
                "July stays as filed");
        assertEquals(-30.0, august.stream().mapToDouble(TcsRecord::getTcsAmount).sum(), 0.001,
                "August carries the reversal");
    }

    @Test
    void resolveTcsGstin_shouldRefuseWhenTheTcsRegistrationIsUnset() {
        // Falling back to the regular GSTIN files GSTR-8 under the wrong registration, which
        // is rejected - and rejected at the deadline.
        GstConfiguration config = new GstConfiguration();
        config.setGstin("33AABCC1234D1Z5");

        assertTrue(config.resolveTcsGstin().isEmpty());
    }

    @Test
    void resolveTcsGstin_shouldRefuseWhenItDuplicatesTheRegularGstin() {
        // Almost certainly someone pasted the regular GSTIN into the TCS field.
        GstConfiguration config = new GstConfiguration();
        config.setGstin("33AABCC1234D1Z5");
        config.setTcsGstin("33AABCC1234D1Z5");

        assertTrue(config.resolveTcsGstin().isEmpty());
    }

    @Test
    void resolveTcsGstin_shouldReturnTheSeparateRegistration() {
        GstConfiguration config = new GstConfiguration();
        config.setGstin("33AABCC1234D1Z5");
        config.setTcsGstin("33AABCC1234D2ZT");

        assertEquals("33AABCC1234D2ZT", config.resolveTcsGstin().orElseThrow());
    }
}
