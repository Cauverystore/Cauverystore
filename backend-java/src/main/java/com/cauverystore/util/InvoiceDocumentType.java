package com.cauverystore.util;

import com.cauverystore.entities.GstInvoiceItem;

import java.util.Collection;

/**
 * What an invoice is called: Tax Invoice, Bill of Supply, or Invoice-cum-Bill of Supply.
 *
 * <p>Rule 49 of the CGST Rules has a supplier of exempt or nil-rated goods issue a bill of
 * supply, which charges no tax. Rule 46 asks for a tax invoice whenever tax is charged. A single
 * document covering both kinds of line is an invoice-cum-bill of supply (Rule 46A).
 *
 * <p>Worked out from the lines, not stored: a line is tax-free when it carries no CGST, SGST,
 * IGST or cess, rate or amount. Existing invoices, which were all issued as tax invoices, keep
 * that title unless every line on them is tax-free, and then they read as a bill of supply.
 *
 * <p>Composition taxpayers also issue a bill of supply, but they cannot supply through an
 * e-commerce operator (section 10(2)(c)), so they never reach this marketplace and are not
 * modelled here.
 */
public final class InvoiceDocumentType {

    public static final String TAX_INVOICE = "Tax Invoice";
    public static final String BILL_OF_SUPPLY = "Bill of Supply";
    public static final String INVOICE_CUM_BILL_OF_SUPPLY = "Invoice-cum-Bill of Supply";

    private InvoiceDocumentType() { }

    public static String titleFor(Collection<GstInvoiceItem> items) {
        if (items == null || items.isEmpty()) return TAX_INVOICE;
        boolean anyTaxed = false, anyFree = false;
        for (GstInvoiceItem it : items) {
            if (taxFree(it)) anyFree = true; else anyTaxed = true;
        }
        if (anyTaxed && anyFree) return INVOICE_CUM_BILL_OF_SUPPLY;
        return anyFree ? BILL_OF_SUPPLY : TAX_INVOICE;
    }

    /** True when no tax of any kind is charged on the line. */
    static boolean taxFree(GstInvoiceItem it) {
        return zero(it.getCgstRate()) && zero(it.getSgstRate()) && zero(it.getIgstRate())
                && zero(it.getCessRate())
                && zero(it.getCgstAmount()) && zero(it.getSgstAmount()) && zero(it.getIgstAmount())
                && zero(it.getCessAmount());
    }

    private static boolean zero(Double v) { return v == null || Math.abs(v) < 0.005; }
}
