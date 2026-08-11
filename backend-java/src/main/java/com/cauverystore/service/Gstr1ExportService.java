package com.cauverystore.service;

import com.cauverystore.entities.CreditNote;
import com.cauverystore.entities.CreditNoteItem;
import com.cauverystore.entities.DebitNote;
import com.cauverystore.entities.DebitNoteItem;
import com.cauverystore.entities.GstInvoice;
import com.cauverystore.entities.GstInvoiceItem;
import com.cauverystore.util.GstComplianceUtil;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds GSTR-1 in the shape the GST offline utility actually accepts.
 *
 * There was already a GSTR-1 endpoint, but it exported our own field names - invoiceNumber,
 * buyerGstin, taxableAmount and so on. That is a readable report and a useless filing: the
 * utility matches on its own column headings, so the file would be rejected on import and the
 * seller would be back to keying a month of invoices in by hand.
 *
 * What the utility wants instead is one CSV per section, with its exact headings, and - the
 * part that is easy to get wrong - <b>one row per invoice per tax rate</b>. An order holding
 * rice at 5% and a kettle at 18% is one invoice but two rows, because GSTR-1 reports taxable
 * value against the rate it was taxed at. Rows are therefore built from the invoice items
 * rather than the invoice header, which carries only a single rate.
 *
 * Sections produced:
 *   b2b   (4A)  supplies to a registered buyer, i.e. one with a GSTIN
 *   b2cl  (5A)  inter-state supplies to consumers above the B2C-large threshold
 *   b2cs  (7)   remaining consumer supplies, aggregated by place of supply and rate
 *   cdn   (9A/9B) credit and debit notes issued in the period, one row per note per rate
 *   hsn   (12)  the HSN-wise summary, which is mandatory and was missing entirely
 *
 * Nothing here is filed automatically. It produces files a human uploads and checks, which is
 * the right division of labour for a return someone signs.
 */
@Service
public class Gstr1ExportService {

    /** The utility parses dates in this format and rejects ISO ones. */
    private static final DateTimeFormatter GST_DATE =
            DateTimeFormatter.ofPattern("dd-MMM-uuuu", Locale.ENGLISH);

    public static final String[] B2B_HEADERS = {
            "GSTIN/UIN of Recipient", "Receiver Name", "Invoice Number", "Invoice date",
            "Invoice Value", "Place Of Supply", "Reverse Charge", "Applicable % of Tax Rate",
            "Invoice Type", "E-Commerce GSTIN", "Rate", "Taxable Value", "Cess Amount"};

    public static final String[] B2CL_HEADERS = {
            "Invoice Number", "Invoice date", "Invoice Value", "Place Of Supply",
            "Applicable % of Tax Rate", "Rate", "Taxable Value", "Cess Amount",
            "E-Commerce GSTIN"};

    public static final String[] B2CS_HEADERS = {
            "Type", "Place Of Supply", "Applicable % of Tax Rate", "Rate", "Taxable Value",
            "Cess Amount", "E-Commerce GSTIN"};

    /**
     * Table 9A/9B - credit and debit notes issued in the period.
     *
     * Both notes share one shape in the offline utility, distinguished by the "Note Type"
     * column: C for a credit note, D for a debit note. Only notes issued against a registered
     * recipient (a GSTIN is present) are reported here; notes to consumers are netted inside
     * B2CS rather than listed.
     */
    public static final String[] CDN_HEADERS = {
            "GSTIN/UIN of Recipient", "Receiver Name", "Note Number", "Note date",
            "Note Value", "Place Of Supply", "Reverse Charge", "Applicable % of Tax Rate",
            "Note Type", "E-Commerce GSTIN", "Rate", "Taxable Value", "Cess Amount"};

    public static final String[] HSN_HEADERS = {
            "HSN", "Description", "UQC", "Total Quantity", "Total Value", "Rate",
            "Taxable Value", "Integrated Tax Amount", "Central Tax Amount",
            "State/UT Tax Amount", "Cess Amount"};

    /** One invoice line collapsed to the rate it was taxed at. */
    private static class RateLine {
        double rate;
        double cess;
        double cessAmount;
        double taxable;
        double igst;
        double cgst;
        double sgst;
        double quantity;
        double totalValue;
        String uqc = "NOS";
        String description;
    }

    /**
     * Splits an invoice into one line per distinct GST rate.
     *
     * The rate is taken from the item, not the invoice header: the header holds a single rate
     * and an order can easily mix 5% staples with 18% electronics.
     *
     * Keyed on rate AND cess, because the two are independent: two HSNs at the same 28% can
     * differ on whether compensation cess applies, and GSTR-1 reports the cess figure against
     * the line it belongs to rather than across the whole rate.
     */
    private Map<String, RateLine> linesByRate(GstInvoice inv) {
        Map<String, RateLine> byRate = new LinkedHashMap<>();
        for (GstInvoiceItem item : inv.getItems()) {
            double rate = nz(item.getIgstRate()) > 0
                    ? nz(item.getIgstRate())
                    : nz(item.getCgstRate()) + nz(item.getSgstRate());
            double cess = nz(item.getCessRate());
            String key = rate + "|" + cess;
            RateLine line = byRate.computeIfAbsent(key, k -> {
                RateLine fresh = new RateLine();
                fresh.rate = rate;
                fresh.cess = cess;
                return fresh;
            });
            line.taxable += nz(item.getTaxableValue());
            line.cessAmount += nz(item.getCessAmount());
            line.igst += nz(item.getIgstAmount());
            line.cgst += nz(item.getCgstAmount());
            line.sgst += nz(item.getSgstAmount());
            line.quantity += item.getQuantity() == null ? 0 : item.getQuantity();
            line.totalValue += nz(item.getTotalAmount());
            if (item.getUnitOfMeasure() != null) line.uqc = item.getUnitOfMeasure();
            if (line.description == null) line.description = item.getProductName();
        }
        return byRate;
    }

    /** 4A - supplies to a buyer who has a GSTIN and can claim input credit on them. */
    public List<Map<String, Object>> b2b(List<GstInvoice> invoices) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (GstInvoice inv : invoices) {
            if (isBlank(inv.getBuyerGstin())) continue;
            for (RateLine line : linesByRate(inv).values()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("GSTIN/UIN of Recipient", inv.getBuyerGstin());
                row.put("Receiver Name", inv.getBuyerName());
                row.put("Invoice Number", inv.getInvoiceNumber());
                row.put("Invoice date", date(inv));
                row.put("Invoice Value", round(nz(inv.getTotalAmount())));
                row.put("Place Of Supply", inv.getPlaceOfSupply());
                row.put("Reverse Charge", "N");
                row.put("Applicable % of Tax Rate", "");
                row.put("Invoice Type", "Regular B2B");
                row.put("E-Commerce GSTIN", "");
                row.put("Rate", round(line.rate));
                row.put("Taxable Value", round(line.taxable));
                row.put("Cess Amount", round(line.cessAmount));
                rows.add(row);
            }
        }
        return rows;
    }

    /** 5A - inter-state consumer supplies above the B2C-large threshold, reported per invoice. */
    public List<Map<String, Object>> b2cl(List<GstInvoice> invoices) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (GstInvoice inv : invoices) {
            if (!isBlank(inv.getBuyerGstin())) continue;
            if (!GstComplianceUtil.isB2cLarge(inv.getIsInterState(), inv.getTaxableAmount())) continue;
            for (RateLine line : linesByRate(inv).values()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("Invoice Number", inv.getInvoiceNumber());
                row.put("Invoice date", date(inv));
                row.put("Invoice Value", round(nz(inv.getTotalAmount())));
                row.put("Place Of Supply", inv.getPlaceOfSupply());
                row.put("Applicable % of Tax Rate", "");
                row.put("Rate", round(line.rate));
                row.put("Taxable Value", round(line.taxable));
                row.put("Cess Amount", round(line.cessAmount));
                row.put("E-Commerce GSTIN", "");
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * 7 - the remaining consumer supplies.
     *
     * Reported in aggregate rather than per invoice, so hundreds of small orders collapse into
     * a handful of rows keyed on place of supply and rate. Getting this wrong by emitting one
     * row per invoice is the usual reason a B2CS import is rejected.
     */
    public List<Map<String, Object>> b2cs(List<GstInvoice> invoices) {
        Map<String, Map<String, Object>> aggregated = new LinkedHashMap<>();
        for (GstInvoice inv : invoices) {
            if (!isBlank(inv.getBuyerGstin())) continue;
            if (GstComplianceUtil.isB2cLarge(inv.getIsInterState(), inv.getTaxableAmount())) continue;
            boolean interState = Boolean.TRUE.equals(inv.getIsInterState());
            for (RateLine line : linesByRate(inv).values()) {
                String key = (interState ? "INTER" : "INTRA") + "|" + inv.getPlaceOfSupply() + "|" + line.rate + "|" + line.cess;
                Map<String, Object> row = aggregated.computeIfAbsent(key, k -> {
                    Map<String, Object> fresh = new LinkedHashMap<>();
                    fresh.put("Type", interState ? "INTER" : "INTRA");
                    fresh.put("Place Of Supply", inv.getPlaceOfSupply());
                    fresh.put("Applicable % of Tax Rate", "");
                    fresh.put("Rate", round(line.rate));
                    fresh.put("Taxable Value", 0.0);
                    fresh.put("Cess Amount", 0.0);
                    fresh.put("E-Commerce GSTIN", "");
                    return fresh;
                });
                row.put("Taxable Value", round((Double) row.get("Taxable Value") + line.taxable));
                row.put("Cess Amount", round((Double) row.get("Cess Amount") + line.cessAmount));
            }
        }
        return new ArrayList<>(aggregated.values());
    }

    /**
     * 12 - the HSN-wise summary, aggregated across every invoice in the period.
     *
     * Mandatory in GSTR-1 and previously not produced at all, which alone would have stopped
     * the return being filed from this data.
     */
    public List<Map<String, Object>> hsnSummary(List<GstInvoice> invoices) {
        Map<String, Map<String, Object>> byHsnAndRate = new LinkedHashMap<>();
        for (GstInvoice inv : invoices) {
            for (GstInvoiceItem item : inv.getItems()) {
                double rate = nz(item.getIgstRate()) > 0
                        ? nz(item.getIgstRate())
                        : nz(item.getCgstRate()) + nz(item.getSgstRate());
                String hsn = item.getHsnCode() != null ? item.getHsnCode() : item.getSacCode();
                if (isBlank(hsn)) continue;
                String key = hsn + "|" + rate;

                Map<String, Object> row = byHsnAndRate.computeIfAbsent(key, k -> {
                    Map<String, Object> fresh = new LinkedHashMap<>();
                    fresh.put("HSN", hsn);
                    fresh.put("Description", item.getProductName());
                    fresh.put("UQC", item.getUnitOfMeasure() != null ? item.getUnitOfMeasure() : "NOS");
                    fresh.put("Total Quantity", 0.0);
                    fresh.put("Total Value", 0.0);
                    fresh.put("Rate", round(rate));
                    fresh.put("Taxable Value", 0.0);
                    fresh.put("Integrated Tax Amount", 0.0);
                    fresh.put("Central Tax Amount", 0.0);
                    fresh.put("State/UT Tax Amount", 0.0);
                    fresh.put("Cess Amount", 0.0);
                    return fresh;
                });
                add(row, "Total Quantity", item.getQuantity() == null ? 0 : item.getQuantity());
                add(row, "Total Value", nz(item.getTotalAmount()));
                add(row, "Taxable Value", nz(item.getTaxableValue()));
                add(row, "Integrated Tax Amount", nz(item.getIgstAmount()));
                add(row, "Central Tax Amount", nz(item.getCgstAmount()));
                add(row, "State/UT Tax Amount", nz(item.getSgstAmount()));
                add(row, "Cess Amount", nz(item.getCessAmount()));
            }
        }
        return new ArrayList<>(byHsnAndRate.values());
    }

    /**
     * 9A/9B - credit and debit notes issued in the period.
     *
     * Reported only against a registered recipient (a GSTIN, not URP): notes to consumers are
     * netted inside B2CS rather than listed. One row per note per rate, so a note that reversed
     * lines at 5% and 18% splits the same way the original invoice did.
     */
    public List<Map<String, Object>> cdn(List<CreditNote> creditNotes, List<DebitNote> debitNotes) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (creditNotes != null) {
            for (CreditNote cn : creditNotes) {
                rows.addAll(cdnRows("C", cn.getBuyerGstin(), cn.getBuyerName(),
                        cn.getCreditNoteNumber(), cn.getCreditNoteDate(), cn.getTotalAmount(),
                        cn.getPlaceOfSupply(), creditItems(cn.getItems())));
            }
        }
        if (debitNotes != null) {
            for (DebitNote dn : debitNotes) {
                rows.addAll(cdnRows("D", dn.getBuyerGstin(), dn.getBuyerName(),
                        dn.getDebitNoteNumber(), dn.getDebitNoteDate(), dn.getTotalAmount(),
                        dn.getPlaceOfSupply(), debitItems(dn.getItems())));
            }
        }
        return rows;
    }

    private static class CdnItem {
        double rate;
        double taxable;
    }

    private List<CdnItem> creditItems(List<CreditNoteItem> items) {
        List<CdnItem> out = new ArrayList<>();
        for (CreditNoteItem it : items) {
            CdnItem c = new CdnItem();
            c.rate = nz(it.getIgstRate()) > 0 ? nz(it.getIgstRate())
                    : nz(it.getCgstRate()) + nz(it.getSgstRate());
            c.taxable = nz(it.getTaxableValue());
            out.add(c);
        }
        return out;
    }

    private List<CdnItem> debitItems(List<DebitNoteItem> items) {
        List<CdnItem> out = new ArrayList<>();
        for (DebitNoteItem it : items) {
            CdnItem c = new CdnItem();
            c.rate = nz(it.getIgstRate()) > 0 ? nz(it.getIgstRate())
                    : nz(it.getCgstRate()) + nz(it.getSgstRate());
            c.taxable = nz(it.getTaxableValue());
            out.add(c);
        }
        return out;
    }

    private List<Map<String, Object>> cdnRows(String noteType, String buyerGstin, String buyerName,
                                              String noteNumber, java.time.LocalDate noteDate,
                                              Double noteValue, String placeOfSupply,
                                              List<CdnItem> items) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (isBlank(buyerGstin) || "URP".equalsIgnoreCase(buyerGstin.trim())) return rows;
        Map<String, Double> taxableByRate = new LinkedHashMap<>();
        for (CdnItem it : items) {
            String key = String.valueOf(it.rate);
            taxableByRate.merge(key, it.taxable, Double::sum);
        }
        for (Map.Entry<String, Double> e : taxableByRate.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("GSTIN/UIN of Recipient", buyerGstin.trim());
            row.put("Receiver Name", buyerName);
            row.put("Note Number", noteNumber);
            row.put("Note date", noteDate == null ? "" : noteDate.format(GST_DATE));
            row.put("Note Value", round(nz(noteValue)));
            row.put("Place Of Supply", placeOfSupply);
            row.put("Reverse Charge", "N");
            row.put("Applicable % of Tax Rate", "");
            row.put("Note Type", noteType);
            row.put("E-Commerce GSTIN", "");
            row.put("Rate", round(Double.parseDouble(e.getKey())));
            row.put("Taxable Value", round(e.getValue()));
            row.put("Cess Amount", 0.0);
            rows.add(row);
        }
        return rows;
    }

    /** Every section, keyed by the name the utility uses for it. */
    public Map<String, List<Map<String, Object>>> allSections(List<GstInvoice> invoices) {
        return allSections(invoices, List.of(), List.of());
    }

    /** Every section, including credit and debit notes for the period. */
    public Map<String, List<Map<String, Object>>> allSections(List<GstInvoice> invoices,
                                                              List<CreditNote> creditNotes,
                                                              List<DebitNote> debitNotes) {
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        out.put("b2b", b2b(invoices));
        out.put("b2cl", b2cl(invoices));
        out.put("b2cs", b2cs(invoices));
        out.put("cdn", cdn(creditNotes, debitNotes));
        out.put("hsn", hsnSummary(invoices));
        return out;
    }

    public String[] headersFor(String section) {
        switch (section == null ? "" : section.toLowerCase()) {
            case "b2b": return B2B_HEADERS;
            case "b2cl": return B2CL_HEADERS;
            case "b2cs": return B2CS_HEADERS;
            case "cdn": return CDN_HEADERS;
            case "hsn": return HSN_HEADERS;
            default: throw new IllegalArgumentException(
                    "Unknown GSTR-1 section '" + section + "'. Use b2b, b2cl, b2cs, cdn or hsn.");
        }
    }

    private void add(Map<String, Object> row, String key, double amount) {
        row.put(key, round((Double) row.get(key) + amount));
    }

    private String date(GstInvoice inv) {
        return inv.getInvoiceDate() == null ? "" : inv.getInvoiceDate().format(GST_DATE);
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private static double nz(Double d) { return d == null ? 0.0 : d; }
    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }
}
