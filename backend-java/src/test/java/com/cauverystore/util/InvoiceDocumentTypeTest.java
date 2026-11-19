package com.cauverystore.util;

import com.cauverystore.entities.GstInvoiceItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Rule 49 CGST Rules: no tax charged means a bill of supply, not a tax invoice. */
class InvoiceDocumentTypeTest {

    private static GstInvoiceItem taxed() {
        GstInvoiceItem i = new GstInvoiceItem();
        i.setCgstRate(2.5); i.setSgstRate(2.5); i.setCgstAmount(25.0); i.setSgstAmount(25.0);
        return i;
    }

    private static GstInvoiceItem taxFree() {
        GstInvoiceItem i = new GstInvoiceItem();
        i.setCgstRate(0.0); i.setSgstRate(0.0); i.setIgstRate(0.0);
        i.setCgstAmount(0.0); i.setSgstAmount(0.0); i.setIgstAmount(0.0);
        return i;
    }

    @Test
    void aTaxedInvoiceIsATaxInvoice() {
        assertEquals(InvoiceDocumentType.TAX_INVOICE, InvoiceDocumentType.titleFor(List.of(taxed(), taxed())));
    }

    @Test
    void allTaxFreeLinesMakeABillOfSupply() {
        assertEquals(InvoiceDocumentType.BILL_OF_SUPPLY, InvoiceDocumentType.titleFor(List.of(taxFree())));
    }

    @Test
    void aMixIsAnInvoiceCumBillOfSupply() {
        assertEquals(InvoiceDocumentType.INVOICE_CUM_BILL_OF_SUPPLY,
                InvoiceDocumentType.titleFor(List.of(taxed(), taxFree())));
    }

    @Test
    void missingTaxFiguresCountAsTaxFree() {
        assertEquals(InvoiceDocumentType.BILL_OF_SUPPLY,
                InvoiceDocumentType.titleFor(List.of(new GstInvoiceItem())));
    }

    @Test
    void integratedTaxAloneMakesALineTaxed() {
        GstInvoiceItem i = new GstInvoiceItem();
        i.setIgstRate(5.0); i.setIgstAmount(50.0);
        assertEquals(InvoiceDocumentType.TAX_INVOICE, InvoiceDocumentType.titleFor(List.of(i)));
    }

    @Test
    void noLinesStaysATaxInvoice_soAnUnloadedInvoiceIsNeverRelabelled() {
        assertEquals(InvoiceDocumentType.TAX_INVOICE, InvoiceDocumentType.titleFor(List.of()));
        assertEquals(InvoiceDocumentType.TAX_INVOICE, InvoiceDocumentType.titleFor(null));
    }
}
