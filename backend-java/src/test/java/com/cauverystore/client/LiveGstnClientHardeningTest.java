package com.cauverystore.client;

import com.cauverystore.entities.GstInvoice;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LiveGstnClientHardeningTest {

    private final LiveGstnClient client = new LiveGstnClient();

    private GstInvoice invoice() {
        GstInvoice inv = new GstInvoice();
        inv.setInvoiceNumber("CS/7");
        inv.setInvoiceDate(LocalDate.of(2026, 11, 1));
        inv.setSellerGstin("33AABCU9603R1ZM");
        inv.setBuyerGstin("33AAACR5055K1Z5");
        inv.setPlaceOfSupply("33-Tamil Nadu");
        inv.setTotalAmount(105.0);
        inv.setTaxableAmount(100.0);
        return inv;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> part(Map<String, Object> doc, String key) {
        return (Map<String, Object>) doc.get(key);
    }

    @Test
    void aMarketplaceSaleNamesTheOperator() {
        GstInvoice inv = invoice();
        inv.setEcoGstin("33abcde1234f1z7");
        assertEquals("33ABCDE1234F1Z7", part(client.buildInvoicePayload(inv), "TranDtls").get("EcmGstin"));
    }

    @Test
    void noOperatorWhenTheSellerIsTheOperator() {
        GstInvoice inv = invoice();
        assertNull(part(client.buildInvoicePayload(inv), "TranDtls").get("EcmGstin"));
        inv.setEcoGstin(inv.getSellerGstin());
        assertNull(part(client.buildInvoicePayload(inv), "TranDtls").get("EcmGstin"));
    }

    @Test
    void aCreditNoteGoesAsCrnAndNamesTheInvoiceItAdjusts() {
        GstInvoice cn = invoice();
        cn.setIrpDocType("CRN");
        cn.setInvoiceNumber("CN/3");
        cn.setPrecedingInvoiceNumber("CS/7");
        cn.setPrecedingInvoiceDate(LocalDate.of(2026, 10, 20));

        Map<String, Object> doc = client.buildInvoicePayload(cn);

        assertEquals("CRN", part(doc, "DocDtls").get("Typ"));
        assertEquals("CN/3", part(doc, "DocDtls").get("No"));
        Map<String, Object> prec = (Map<String, Object>) ((List<?>) part(doc, "RefDtls").get("PrecDocDtls")).get(0);
        assertEquals("CS/7", prec.get("InvNo"));
        assertEquals("20/10/2026", prec.get("InvDt"));
    }

    @Test
    void anInvoiceHasNoReferenceBlockAndAnUnknownTypeIsSentAsInv() {
        GstInvoice inv = invoice();
        inv.setIrpDocType("XYZ");
        Map<String, Object> doc = client.buildInvoicePayload(inv);
        assertEquals("INV", part(doc, "DocDtls").get("Typ"));
        assertNull(doc.get("RefDtls"));
    }

    @Test
    void aDuplicateIsRecognisedWithTheIrnItAlreadyHas() {
        Map<String, Object> body = Map.of(
                "Status", "0",
                "ErrorDetails", List.of(Map.of("ErrorCode", "2150", "ErrorMessage", "Duplicate IRN")),
                "InfoDtls", List.of(Map.of("InfCd", "DUPIRN",
                        "Desc", Map.of("AckNo", 112010000000001L, "AckDt", "2026-11-01 10:00:00", "Irn", "c".repeat(64)))));
        assertEquals("c".repeat(64), LiveGstnClient.duplicateIrn(body));
    }

    @Test
    void anyOtherRefusalIsNotTakenForADuplicate() {
        assertNull(LiveGstnClient.duplicateIrn(Map.of("Status", "0",
                "ErrorDetails", List.of(Map.of("ErrorCode", "2258", "ErrorMessage", "Invalid state code")))));
        assertNull(LiveGstnClient.duplicateIrn(Map.of("Status", "0",
                "ErrorDetails", List.of(Map.of("ErrorCode", "2150")))), "no IRN to recover");
        assertNull(LiveGstnClient.duplicateIrn(null));
    }
}
