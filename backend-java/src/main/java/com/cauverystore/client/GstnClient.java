package com.cauverystore.client;

import com.cauverystore.entities.GstInvoice;

import java.time.LocalDate;
import java.util.Map;

public interface GstnClient {

    String getName();

    default boolean isSimulated() {
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
