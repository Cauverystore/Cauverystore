package com.cauverystore.service;

import com.cauverystore.entities.CreditNote;
import com.cauverystore.entities.CreditNoteItem;
import com.cauverystore.entities.DebitNote;
import com.cauverystore.entities.DebitNoteItem;
import com.cauverystore.entities.GstInvoice;
import com.cauverystore.entities.GstInvoiceItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tally Prime import files built from the seller's own invoices and notes.
 *
 * Two files, imported in order through Gateway of Tally - Import - Masters, then Transactions:
 *   ledgers   the sales, tax, round-off and customer ledgers the vouchers post to
 *   vouchers  one Sales voucher per invoice, one Credit Note / Debit Note voucher per note
 *
 * Vouchers are accounting vouchers (no stock items), so they import into a company that does
 * not track inventory in Tally. Figures are the ones already on the invoices - nothing is
 * recalculated - and every voucher balances: any paisa left between the total and its parts
 * goes to a Round Off ledger.
 *
 * Ledger names are fixed so repeated exports post to the same ledgers. Tally treats an
 * existing ledger in the masters file as a duplicate and skips it.
 */
@Service
public class TallyExportService {

    public static final String CONSUMER_LEDGER = "Cauvery Store Customers";
    public static final String ROUND_OFF_LEDGER = "Round Off";
    public static final String CGST_LEDGER = "Output CGST";
    public static final String SGST_LEDGER = "Output SGST";
    public static final String IGST_LEDGER = "Output IGST";
    public static final String CESS_LEDGER = "Output Cess";

    private static final DateTimeFormatter TALLY_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    /** One ledger line: credit when amount is positive, debit when negative. */
    record Entry(String ledger, BigDecimal credit) { }

    /** A voucher before it is written out. */
    record Voucher(String type, String number, LocalDate date, String party, String partyGstin,
                   String placeOfSupply, String narration, List<Entry> entries,
                   String billRef, boolean newBill) { }

    // ---------------------------------------------------------------- vouchers

    public String vouchers(List<GstInvoice> invoices, List<CreditNote> creditNotes,
                           List<DebitNote> debitNotes) {
        List<Voucher> out = new ArrayList<>();
        for (GstInvoice inv : invoices) {
            if ("CANCELLED".equalsIgnoreCase(inv.getStatus())) continue;
            out.add(salesVoucher(inv));
        }
        for (CreditNote cn : creditNotes == null ? List.<CreditNote>of() : creditNotes) {
            out.add(creditNoteVoucher(cn));
        }
        for (DebitNote dn : debitNotes == null ? List.<DebitNote>of() : debitNotes) {
            out.add(debitNoteVoucher(dn));
        }
        StringBuilder x = new StringBuilder();
        open(x, "Vouchers");
        for (Voucher v : out) writeVoucher(x, v);
        close(x);
        return x.toString();
    }

    Voucher salesVoucher(GstInvoice inv) {
        Map<String, BigDecimal> sales = new LinkedHashMap<>();
        for (GstInvoiceItem it : inv.getItems() == null ? List.<GstInvoiceItem>of() : inv.getItems()) {
            double rate = rate(it.getIgstRate(), it.getCgstRate(), it.getSgstRate());
            sales.merge(salesLedger(isService(it.getHsnCode(), it.getSacCode()), rate),
                    money(it.getTaxableValue()), BigDecimal::add);
        }
        List<Entry> entries = new ArrayList<>();
        BigDecimal total = money(inv.getTotalAmount());
        entries.add(new Entry(party(inv.getBuyerGstin(), inv.getBuyerName()), total.negate()));
        sales.forEach((ledger, amount) -> entries.add(new Entry(ledger, amount)));
        addTax(entries, inv.getCgstAmount(), inv.getSgstAmount(), inv.getIgstAmount(), inv.getTotalCess());
        balance(entries);
        return new Voucher("Sales", inv.getInvoiceNumber(), inv.getInvoiceDate(),
                party(inv.getBuyerGstin(), inv.getBuyerName()), registered(inv.getBuyerGstin()),
                stateName(inv.getPlaceOfSupply()),
                "Cauvery Store invoice " + inv.getInvoiceNumber(), entries,
                inv.getInvoiceNumber(), true);
    }

    Voucher creditNoteVoucher(CreditNote cn) {
        Map<String, BigDecimal> sales = new LinkedHashMap<>();
        for (CreditNoteItem it : cn.getItems() == null ? List.<CreditNoteItem>of() : cn.getItems()) {
            double rate = rate(it.getIgstRate(), it.getCgstRate(), it.getSgstRate());
            sales.merge(salesLedger(false, rate), money(it.getTaxableValue()), BigDecimal::add);
        }
        if (sales.isEmpty()) {
            sales.put(salesLedger(false, rate(cn.getIgstRate(), cn.getCgstRate(), cn.getSgstRate())),
                    money(cn.getTaxableAmount()));
        }
        // The mirror of a sale: the customer is credited, sales and output tax are debited.
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(party(cn.getBuyerGstin(), cn.getBuyerName()), money(cn.getTotalAmount())));
        sales.forEach((ledger, amount) -> entries.add(new Entry(ledger, amount.negate())));
        List<Entry> tax = new ArrayList<>();
        addTax(tax, cn.getCgstAmount(), cn.getSgstAmount(), cn.getIgstAmount(), null);
        tax.forEach(t -> entries.add(new Entry(t.ledger(), t.credit().negate())));
        balance(entries);
        return new Voucher("Credit Note", cn.getCreditNoteNumber(), cn.getCreditNoteDate(),
                party(cn.getBuyerGstin(), cn.getBuyerName()), registered(cn.getBuyerGstin()),
                stateName(cn.getPlaceOfSupply()),
                "Against invoice " + nz(cn.getOriginalInvoiceNumber())
                        + (cn.getReason() == null ? "" : " - " + cn.getReason()), entries,
                // Settles against the invoice it reduces, so the customer's bill nets off.
                isBlankStr(cn.getOriginalInvoiceNumber()) ? cn.getCreditNoteNumber() : cn.getOriginalInvoiceNumber(),
                isBlankStr(cn.getOriginalInvoiceNumber()));
    }

    Voucher debitNoteVoucher(DebitNote dn) {
        Map<String, BigDecimal> sales = new LinkedHashMap<>();
        for (DebitNoteItem it : dn.getItems() == null ? List.<DebitNoteItem>of() : dn.getItems()) {
            double rate = rate(it.getIgstRate(), it.getCgstRate(), it.getSgstRate());
            sales.merge(salesLedger(false, rate), money(it.getTaxableValue()), BigDecimal::add);
        }
        if (sales.isEmpty()) {
            sales.put(salesLedger(false, rate(dn.getIgstRate(), dn.getCgstRate(), dn.getSgstRate())),
                    money(dn.getTaxableAmount()));
        }
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(party(dn.getBuyerGstin(), dn.getBuyerName()), money(dn.getTotalAmount()).negate()));
        sales.forEach((ledger, amount) -> entries.add(new Entry(ledger, amount)));
        addTax(entries, dn.getCgstAmount(), dn.getSgstAmount(), dn.getIgstAmount(), null);
        balance(entries);
        return new Voucher("Debit Note", dn.getDebitNoteNumber(), dn.getDebitNoteDate(),
                party(dn.getBuyerGstin(), dn.getBuyerName()), registered(dn.getBuyerGstin()),
                stateName(dn.getPlaceOfSupply()),
                "Against invoice " + nz(dn.getOriginalInvoiceNumber()), entries,
                dn.getDebitNoteNumber(), true);
    }

    // ---------------------------------------------------------------- ledgers

    /** Every ledger the vouchers for these documents post to. */
    public String ledgers(List<GstInvoice> invoices, List<CreditNote> creditNotes,
                          List<DebitNote> debitNotes) {
        Set<String> sales = new LinkedHashSet<>();
        Map<String, String[]> parties = new LinkedHashMap<>(); // name -> {gstin, state}
        for (GstInvoice inv : invoices) {
            if ("CANCELLED".equalsIgnoreCase(inv.getStatus())) continue;
            Voucher v = salesVoucher(inv);
            v.entries().forEach(e -> { if (isSalesLedger(e.ledger())) sales.add(e.ledger()); });
            parties.putIfAbsent(v.party(), new String[]{v.partyGstin(), v.placeOfSupply()});
        }
        for (CreditNote cn : creditNotes == null ? List.<CreditNote>of() : creditNotes) {
            Voucher v = creditNoteVoucher(cn);
            v.entries().forEach(e -> { if (isSalesLedger(e.ledger())) sales.add(e.ledger()); });
            parties.putIfAbsent(v.party(), new String[]{v.partyGstin(), v.placeOfSupply()});
        }
        for (DebitNote dn : debitNotes == null ? List.<DebitNote>of() : debitNotes) {
            Voucher v = debitNoteVoucher(dn);
            v.entries().forEach(e -> { if (isSalesLedger(e.ledger())) sales.add(e.ledger()); });
            parties.putIfAbsent(v.party(), new String[]{v.partyGstin(), v.placeOfSupply()});
        }

        StringBuilder x = new StringBuilder();
        open(x, "All Masters");
        for (String s : sales) {
            ledger(x, s, "Sales Accounts", null, null, null);
        }
        ledger(x, CGST_LEDGER, "Duties &amp; Taxes", "Central Tax", null, null);
        ledger(x, SGST_LEDGER, "Duties &amp; Taxes", "State Tax", null, null);
        ledger(x, IGST_LEDGER, "Duties &amp; Taxes", "Integrated Tax", null, null);
        ledger(x, CESS_LEDGER, "Duties &amp; Taxes", "Cess", null, null);
        ledger(x, ROUND_OFF_LEDGER, "Indirect Expenses", null, null, null);
        for (Map.Entry<String, String[]> p : parties.entrySet()) {
            ledger(x, p.getKey(), "Sundry Debtors", null, p.getValue()[0], p.getValue()[1]);
        }
        close(x);
        return x.toString();
    }

    private void ledger(StringBuilder x, String name, String parent, String dutyHead,
                        String gstin, String state) {
        x.append("<TALLYMESSAGE xmlns:UDF=\"TallyUDF\">\n");
        x.append(" <LEDGER NAME=\"").append(esc(name)).append("\" ACTION=\"Create\">\n");
        x.append("  <NAME.LIST><NAME>").append(esc(name)).append("</NAME></NAME.LIST>\n");
        // parent is passed already escaped where it holds an ampersand
        x.append("  <PARENT>").append(parent).append("</PARENT>\n");
        if (dutyHead != null) {
            x.append("  <TAXTYPE>GST</TAXTYPE>\n");
            x.append("  <GSTDUTYHEAD>").append(dutyHead).append("</GSTDUTYHEAD>\n");
        }
        if ("Sundry Debtors".equals(parent)) {
            x.append("  <ISBILLWISEON>Yes</ISBILLWISEON>\n");
            if (gstin != null) {
                x.append("  <GSTREGISTRATIONTYPE>Regular</GSTREGISTRATIONTYPE>\n");
                x.append("  <PARTYGSTIN>").append(esc(gstin)).append("</PARTYGSTIN>\n");
            } else {
                x.append("  <GSTREGISTRATIONTYPE>Consumer</GSTREGISTRATIONTYPE>\n");
            }
            if (state != null && gstin != null) {
                x.append("  <LEDSTATENAME>").append(esc(state)).append("</LEDSTATENAME>\n");
            }
        }
        x.append(" </LEDGER>\n</TALLYMESSAGE>\n");
    }

    // ---------------------------------------------------------------- writing

    private void open(StringBuilder x, String report) {
        x.append("<ENVELOPE>\n<HEADER><TALLYREQUEST>Import Data</TALLYREQUEST></HEADER>\n<BODY>\n<IMPORTDATA>\n")
                .append("<REQUESTDESC><REPORTNAME>").append(report).append("</REPORTNAME></REQUESTDESC>\n")
                .append("<REQUESTDATA>\n");
    }

    private void close(StringBuilder x) {
        x.append("</REQUESTDATA>\n</IMPORTDATA>\n</BODY>\n</ENVELOPE>\n");
    }

    private void writeVoucher(StringBuilder x, Voucher v) {
        String date = v.date() == null ? "" : v.date().format(TALLY_DATE);
        x.append("<TALLYMESSAGE xmlns:UDF=\"TallyUDF\">\n");
        x.append(" <VOUCHER VCHTYPE=\"").append(esc(v.type()))
                .append("\" ACTION=\"Create\" OBJVIEW=\"Accounting Voucher View\">\n");
        x.append("  <DATE>").append(date).append("</DATE>\n");
        x.append("  <EFFECTIVEDATE>").append(date).append("</EFFECTIVEDATE>\n");
        x.append("  <VOUCHERTYPENAME>").append(esc(v.type())).append("</VOUCHERTYPENAME>\n");
        x.append("  <VOUCHERNUMBER>").append(esc(v.number())).append("</VOUCHERNUMBER>\n");
        x.append("  <REFERENCE>").append(esc(v.number())).append("</REFERENCE>\n");
        x.append("  <PARTYLEDGERNAME>").append(esc(v.party())).append("</PARTYLEDGERNAME>\n");
        if (v.partyGstin() != null) {
            x.append("  <PARTYGSTIN>").append(esc(v.partyGstin())).append("</PARTYGSTIN>\n");
        }
        if (v.placeOfSupply() != null) {
            x.append("  <PLACEOFSUPPLY>").append(esc(v.placeOfSupply())).append("</PLACEOFSUPPLY>\n");
        }
        x.append("  <NARRATION>").append(esc(v.narration())).append("</NARRATION>\n");
        x.append("  <PERSISTEDVIEW>Accounting Voucher View</PERSISTEDVIEW>\n");
        for (Entry e : v.entries()) {
            boolean debit = e.credit().signum() < 0;
            boolean isParty = e.ledger().equals(v.party());
            x.append("  <ALLLEDGERENTRIES.LIST>\n");
            x.append("   <LEDGERNAME>").append(esc(e.ledger())).append("</LEDGERNAME>\n");
            // Tally writes debits as negative amounts marked deemed-positive.
            x.append("   <ISDEEMEDPOSITIVE>").append(debit ? "Yes" : "No").append("</ISDEEMEDPOSITIVE>\n");
            x.append("   <ISPARTYLEDGER>").append(isParty ? "Yes" : "No").append("</ISPARTYLEDGER>\n");
            x.append("   <AMOUNT>").append(e.credit().toPlainString()).append("</AMOUNT>\n");
            if (isParty) {
                x.append("   <BILLALLOCATIONS.LIST><NAME>").append(esc(v.billRef()))
                        .append("</NAME><BILLTYPE>")
                        .append(v.newBill() ? "New Ref" : "Agst Ref")
                        .append("</BILLTYPE><AMOUNT>").append(e.credit().toPlainString())
                        .append("</AMOUNT></BILLALLOCATIONS.LIST>\n");
            }
            x.append("  </ALLLEDGERENTRIES.LIST>\n");
        }
        x.append(" </VOUCHER>\n</TALLYMESSAGE>\n");
    }

    // ---------------------------------------------------------------- helpers

    private void addTax(List<Entry> entries, Double cgst, Double sgst, Double igst, Double cess) {
        if (money(cgst).signum() != 0) entries.add(new Entry(CGST_LEDGER, money(cgst)));
        if (money(sgst).signum() != 0) entries.add(new Entry(SGST_LEDGER, money(sgst)));
        if (money(igst).signum() != 0) entries.add(new Entry(IGST_LEDGER, money(igst)));
        if (money(cess).signum() != 0) entries.add(new Entry(CESS_LEDGER, money(cess)));
    }

    /** Puts any paisa difference on Round Off so the voucher sums to zero. */
    private void balance(List<Entry> entries) {
        BigDecimal sum = entries.stream().map(Entry::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.signum() != 0) entries.add(new Entry(ROUND_OFF_LEDGER, sum.negate()));
    }

    static String salesLedger(boolean service, double rate) {
        String r = BigDecimal.valueOf(rate).stripTrailingZeros().toPlainString();
        return (service ? "Service Income @ " : "Sales @ ") + r + "%";
    }

    private static boolean isSalesLedger(String name) {
        return name.startsWith("Sales @ ") || name.startsWith("Service Income @ ");
    }

    private static boolean isService(String hsn, String sac) {
        return (hsn == null || hsn.isBlank()) && sac != null && !sac.isBlank();
    }

    private static double rate(Double igst, Double cgst, Double sgst) {
        double i = igst == null ? 0 : igst;
        return i > 0 ? i : (cgst == null ? 0 : cgst) + (sgst == null ? 0 : sgst);
    }

    /** The buyer's own ledger when registered; all consumers share one. */
    static String party(String gstin, String name) {
        if (registered(gstin) == null) return CONSUMER_LEDGER;
        return name == null || name.isBlank() ? gstin.trim().toUpperCase() : name.trim();
    }

    private static String registered(String gstin) {
        if (gstin == null || gstin.isBlank() || "URP".equalsIgnoreCase(gstin.trim())) return null;
        return gstin.trim().toUpperCase();
    }

    /** "33-Tamil Nadu" -> "Tamil Nadu", the way Tally names states. */
    static String stateName(String placeOfSupply) {
        if (placeOfSupply == null || placeOfSupply.isBlank()) return null;
        int dash = placeOfSupply.indexOf('-');
        return dash >= 0 ? placeOfSupply.substring(dash + 1).trim() : placeOfSupply.trim();
    }

    private static BigDecimal money(Double d) {
        return BigDecimal.valueOf(d == null ? 0.0 : d).setScale(2, RoundingMode.HALF_UP);
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static boolean isBlankStr(String s) { return s == null || s.isBlank(); }

    static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&': b.append("&amp;"); break;
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '"': b.append("&quot;"); break;
                case '\'': b.append("&apos;"); break;
                default:
                    // Characters XML 1.0 cannot carry at all are dropped rather than breaking
                    // the whole import over one stray control code in a product name.
                    if (c >= 0x20 || c == '\n' || c == '\t') b.append(c);
            }
        }
        return b.toString();
    }
}
