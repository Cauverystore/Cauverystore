package com.cauverystore.client;

import com.cauverystore.entities.GstInvoice;

import java.time.LocalDate;
import java.util.Map;

public interface GstnClient {

    String getName();

    default boolean isSimulated() {
        return true;
    }

    /**
     * Whether this client can actually call its portal right now.
     *
     * The simulator needs nothing and is always ready. The live client answers whether all six
     * of its credentials are set; the readiness screen reads this to tell an operator whether
     * the e-invoice portal is really on, because "configured" and "selected" are different
     * things and only one of them registers invoices.
     */
    default boolean isConfigured() {
        return true;
    }

    Map<String, Object> generateIrn(GstInvoice invoice);

    Map<String, Object> generateEwayBill(GstInvoice invoice);

    Map<String, Object> validateGstin(String gstin);

    Map<String, Object> fetchGstr2bData(String gstin, String period);

    Map<String, Object> fetchGstr9Data(String gstin, String period);

    Map<String, Object> fetchGstr8Data(String gstin, String period);

    Map<String, Object> fileReturn(String gstin, String form, String period);

    LocalDate getFilingDueDate(String form, String period);
}
