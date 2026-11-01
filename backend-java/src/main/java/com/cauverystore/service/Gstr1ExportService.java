package com.cauverystore.service;

import com.cauverystore.entities.CreditNote;
import com.cauverystore.entities.CreditNoteItem;
import com.cauverystore.entities.DebitNote;
import com.cauverystore.entities.DebitNoteItem;
import com.cauverystore.entities.GstInvoice;
import com.cauverystore.entities.GstInvoiceItem;
import com.cauverystore.client.LiveGstnClient;
import com.cauverystore.util.GstComplianceUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
 *   cdn   (9B)  credit and debit notes to registered buyers, in the utility's cdnr layout
 *   cdnur (9B)  credit and debit notes against B2CL invoices
 *   hsn_b2b (12) the HSN-wise summary of supplies to registered buyers
 *   hsn_b2c (12) the HSN-wise summary of supplies to everyone else
 *   hsn   (12)  the single HSN summary, only for return periods before May 2025
 *   eco   (14)  supplies made through the marketplace, which collects TCS on them (s.52)
 *
 * Column order and values follow the templates shipped with the GST Returns Offline Tool
 * (V3.2.4). The utility reads columns by position, so a correct heading in the wrong place
 * still fails the import.
 *
 * From the May 2025 return period the portal takes Table 12 as two lists, B2B and B2C, and
 * rejects a file that still sends one. A supply is B2B when the buyer has a GSTIN - the same
 * test the b2b section uses, so the two tables always agree.
 *
 * Nothing here is filed automatically. It produces files a human uploads and checks, which is
 * the right division of labour for a return someone signs.
 */
@Service
public class Gstr1ExportService {

    /** The utility parses dates in this format and rejects ISO ones. */
    private static final DateTimeFormatter GST_DATE =
            DateTimeFormatter.ofPattern("dd-MMM-uuuu", Locale.ENGLISH);

    /** The first return period whose HSN summary is split into B2B and B2C. */
    public static final LocalDate HSN_SPLIT_FROM = LocalDate.of(2025, 5, 1);

    public static final String[] B2B_HEADERS = {
            "GSTIN/UIN of Recipient", "Receiver Name", "Invoice Number", "Invoice date",
            "Invoice Value", "Place Of Supply", "Reverse Charge", "Applicable % of Tax Rate",
            "Invoice Type", "E-Commerce GSTIN", "Rate", "Taxable Value", "Cess Amount"};

    public static final String[] B2CL_HEADERS = {
            "Invoice Number", "Invoice date", "Invoice Value", "Place Of Supply",
            "Applicable % of Tax Rate", "Rate", "Taxable Value", "Cess Amount",
            "E-Commerce GSTIN"};

    /**
     * Type is E for sales made through an e-commerce operator, whose GSTIN then goes in the
     * last column, and OE for everything else.
     */
    public static final String[] B2CS_HEADERS = {
            "Type", "Place Of Supply", "Rate", "Applicable % of Tax Rate", "Taxable Value",
            "Cess Amount", "E-Commerce GSTIN"};

    /**
     * Table 9A/9B - credit and debit notes issued in the period.
     *
     * The utility's cdnr layout. Both notes share it, told apart by "Note Type": C for a
     * credit note, D for a debit note. Only notes to a registered recipient (a GSTIN is
     * present) go here. Notes to consumers go to B2CS (netted) or CDNUR (listed), depending on
     * whether the original invoice was B2CS or B2CL.
     */
    public static final String[] CDN_HEADERS = {
            "GSTIN/UIN of Recipient", "Receiver Name", "Note Number", "Note Date",
            "Note Type", "Place Of Supply", "Reverse Charge", "Note Supply Type",
            "Note Value", "Applicable % of Tax Rate", "Rate", "Taxable Value", "Cess Amount"};

    /**
     * Table 9B, unregistered: notes against a B2CL invoice (or an export). Notes against a
     * small consumer invoice do not go here; they are netted into B2CS instead.
     */
    public static final String[] CDNUR_HEADERS = {
            "UR Type", "Note Number", "Note Date", "Note Type", "Place Of Supply",
            "Note Value", "Applicable % of Tax Rate", "Rate", "Taxable Value", "Cess Amount"};

    /** Rate comes last, and UQC is written as code and name, e.g. "NOS-NUMBERS". */
    public static final String[] HSN_HEADERS = {
            "HSN", "Description", "UQC", "Total Quantity", "Total Value", "Taxable Value",
            "Integrated Tax Amount", "Central Tax Amount", "State/UT Tax Amount",
            "Cess Amount", "Rate"};

    /** Table 14: supplies made through an e-commerce operator. */
    public static final String[] ECO_HEADERS = {
            "Nature of Supply", "GSTIN of E-Commerce Operator", "E-Commerce Operator Name",
            "Net value of supplies", "Integrated tax", "Central tax", "State/UT tax", "Cess"};

    /** The utility's wording for supplies on which the operator collects TCS. */
    public static final String ECO_TCS = "Liable to collect tax u/s 52(TCS)";

    /** Unit names from the GST unit master, by code. */
    private static final Map<String, String> UNIT_NAMES = loadUnitNames();

    private static Map<String, String> loadUnitNames() {
        Map<String, String> names = new LinkedHashMap<>();
        try (InputStream in = Gstr1ExportService.class.getClassLoader()
                .getResourceAsStream("master-data/unit_master.json")) {
            if (in != null) {
                for (JsonNode n : new ObjectMapper().readTree(in)) {
                    names.put(n.path("code").asText(), n.path("description").asText());
                }
            }
        } catch (java.io.IOException ignored) {
            // Falls back to the code alone; the import then names the unit it cannot read.
        }
        return names;
    }

    /** A unit as the HSN table wants it: "NOS-NUMBERS", "KGS-KILOGRAMS". */
    static String uqcLabel(String unit) {
        String code = LiveGstnClient.uqc(unit);
        String name = UNIT_NAMES.get(code);
        return name == null || name.isEmpty() ? code : code + "-" + name;
    }

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
        return b2b(invoices, null);
    }

    /** @param ecoGstin the marketplace's TCS GSTIN when these sales went through it, else null */
    public List<Map<String, Object>> b2b(List<GstInvoice> invoices, String ecoGstin) {
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
                row.put("E-Commerce GSTIN", ecoGstin == null ? "" : ecoGstin);
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
        return b2cl(invoices, null);
    }

    public List<Map<String, Object>> b2cl(List<GstInvoice> invoices, String ecoGstin) {
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
                row.put("E-Commerce GSTIN", ecoGstin == null ? "" : ecoGstin);
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
        return b2cs(invoices, null);
    }

    /**
     * @param ecoGstin the marketplace's TCS GSTIN when these sales went through it. Rows are
     *                 then Type E with that GSTIN; without one they are OE.
     */
    public List<Map<String, Object>> b2cs(List<GstInvoice> invoices, String ecoGstin) {
        return b2cs(invoices, ecoGstin, List.of(), Set.of());
    }

    /**
     * As above, with the period's consumer notes netted in. A credit note to a consumer is
     * not listed anywhere else in GSTR-1 unless its invoice was B2CL, so leaving it out of
     * B2CS would report the returned sale as if it had stood.
     *
     * @param b2clOriginals invoice numbers that were reported in B2CL; notes against those
     *                      go to CDNUR instead
     */
    public List<Map<String, Object>> b2cs(List<GstInvoice> invoices, String ecoGstin,
                                          List<NoteDoc> notes, Set<String> b2clOriginals) {
        String type = ecoGstin == null ? "OE" : "E";
        Map<String, Map<String, Object>> aggregated = new LinkedHashMap<>();
        for (GstInvoice inv : invoices) {
            if (!isBlank(inv.getBuyerGstin())) continue;
            if (GstComplianceUtil.isB2cLarge(inv.getIsInterState(), inv.getTaxableAmount())) continue;
            for (RateLine line : linesByRate(inv).values()) {
                Map<String, Object> row = b2csRow(aggregated, type, ecoGstin,
                        inv.getPlaceOfSupply(), line.rate, line.cess);
                row.put("Taxable Value", round((Double) row.get("Taxable Value") + line.taxable));
                row.put("Cess Amount", round((Double) row.get("Cess Amount") + line.cessAmount));
            }
        }
        for (NoteDoc note : notes) {
            if (note.registered() || b2clOriginals.contains(note.original)) continue;
            for (NoteLine line : note.lines) {
                Map<String, Object> row = b2csRow(aggregated, type, ecoGstin,
                        note.placeOfSupply, line.rate, 0.0);
                row.put("Taxable Value", round((Double) row.get("Taxable Value") + note.sign * line.taxable));
            }
        }
        return new ArrayList<>(aggregated.values());
    }

    private static Map<String, Object> b2csRow(Map<String, Map<String, Object>> aggregated, String type,
                                               String ecoGstin, String pos, double rate, double cess) {
        return aggregated.computeIfAbsent(pos + "|" + rate + "|" + cess, k -> {
            Map<String, Object> fresh = new LinkedHashMap<>();
            fresh.put("Type", type);
            fresh.put("Place Of Supply", pos);
            fresh.put("Rate", round(rate));
            fresh.put("Applicable % of Tax Rate", "");
            fresh.put("Taxable Value", 0.0);
            fresh.put("Cess Amount", 0.0);
            fresh.put("E-Commerce GSTIN", ecoGstin == null ? "" : ecoGstin);
            return fresh;
        });
    }

    /**
     * 9B unregistered - notes against invoices that were reported in B2CL. Notes against
     * small consumer invoices are netted into B2CS instead and do not appear here.
     */
    public List<Map<String, Object>> cdnur(List<NoteDoc> notes, Set<String> b2clOriginals) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (NoteDoc note : notes) {
            if (note.registered() || !b2clOriginals.contains(note.original)) continue;
            Map<String, Double> taxableByRate = new LinkedHashMap<>();
            for (NoteLine line : note.lines) taxableByRate.merge(String.valueOf(line.rate), line.taxable, Double::sum);
            for (Map.Entry<String, Double> e : taxableByRate.entrySet()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("UR Type", "B2CL");
                row.put("Note Number", note.number);
                row.put("Note Date", note.date == null ? "" : note.date.format(GST_DATE));
                row.put("Note Type", note.type);
                row.put("Place Of Supply", note.placeOfSupply);
                row.put("Note Value", round(note.value));
                row.put("Applicable % of Tax Rate", "");
                row.put("Rate", round(Double.parseDouble(e.getKey())));
                row.put("Taxable Value", round(e.getValue()));
                row.put("Cess Amount", 0.0);
                rows.add(row);
            }
        }
        return rows;
    }

    /** One line of a credit or debit note, in the shape the summaries need. */
    public static class NoteLine {
        String hsn;
        String description;
        String uqc;
        double quantity;
        double totalValue;
        double taxable;
        double igst;
        double cgst;
        double sgst;
        double rate;
    }

    /**
     * A credit or debit note seen the same way: sign is -1 for a credit note (it takes value
     * away from the period) and +1 for a debit note.
     */
    public static class NoteDoc {
        int sign;
        String type;
        String buyerGstin;
        String number;
        LocalDate date;
        double value;
        String placeOfSupply;
        String original;
        boolean interState;
        List<NoteLine> lines = new ArrayList<>();

        boolean registered() {
            return !isBlank(buyerGstin) && !"URP".equalsIgnoreCase(buyerGstin.trim());
        }
    }

    /** Credit and debit notes as NoteDocs, leaving out any that were cancelled. */
    public static List<NoteDoc> notes(List<CreditNote> creditNotes, List<DebitNote> debitNotes) {
        List<NoteDoc> out = new ArrayList<>();
        for (CreditNote cn : creditNotes == null ? List.<CreditNote>of() : creditNotes) {
            if (cancelled(cn.getStatus())) continue;
            NoteDoc d = noteDoc(-1, "C", cn.getBuyerGstin(), cn.getCreditNoteNumber(), cn.getCreditNoteDate(),
                    cn.getTotalAmount(), cn.getPlaceOfSupply(), cn.getOriginalInvoiceNumber());
            d.interState = Boolean.TRUE.equals(cn.getIsInterState());
            for (CreditNoteItem it : cn.getItems() == null ? List.<CreditNoteItem>of() : cn.getItems()) {
                d.lines.add(noteLine(it.getHsnCode(), it.getSacCode(), it.getProductName(), it.getUnitOfMeasure(),
                        it.getQuantity(), it.getTotalAmount(), it.getTaxableValue(), it.getIgstAmount(),
                        it.getCgstAmount(), it.getSgstAmount(), it.getIgstRate(), it.getCgstRate(), it.getSgstRate()));
            }
            if (d.lines.isEmpty()) d.lines.add(headerLine(cn.getTaxableAmount(), cn.getIgstAmount(),
                    cn.getCgstAmount(), cn.getSgstAmount(), cn.getIgstRate(), cn.getCgstRate(), cn.getSgstRate(),
                    cn.getTotalAmount()));
            out.add(d);
        }
        for (DebitNote dn : debitNotes == null ? List.<DebitNote>of() : debitNotes) {
            if (cancelled(dn.getStatus())) continue;
            NoteDoc d = noteDoc(1, "D", dn.getBuyerGstin(), dn.getDebitNoteNumber(), dn.getDebitNoteDate(),
                    dn.getTotalAmount(), dn.getPlaceOfSupply(), dn.getOriginalInvoiceNumber());
            d.interState = Boolean.TRUE.equals(dn.getIsInterState());
            for (DebitNoteItem it : dn.getItems() == null ? List.<DebitNoteItem>of() : dn.getItems()) {
                d.lines.add(noteLine(it.getHsnCode(), it.getSacCode(), it.getProductName(), it.getUnitOfMeasure(),
                        it.getQuantity(), it.getTotalAmount(), it.getTaxableValue(), it.getIgstAmount(),
                        it.getCgstAmount(), it.getSgstAmount(), it.getIgstRate(), it.getCgstRate(), it.getSgstRate()));
            }
            if (d.lines.isEmpty()) d.lines.add(headerLine(dn.getTaxableAmount(), dn.getIgstAmount(),
                    dn.getCgstAmount(), dn.getSgstAmount(), dn.getIgstRate(), dn.getCgstRate(), dn.getSgstRate(),
                    dn.getTotalAmount()));
            out.add(d);
        }
        return out;
    }

    private static NoteDoc noteDoc(int sign, String type, String gstin, String number, LocalDate date,
                                   Double value, String pos, String original) {
        NoteDoc d = new NoteDoc();
        d.sign = sign;
        d.type = type;
        d.buyerGstin = gstin;
        d.number = number;
        d.date = date;
        d.value = nz(value);
        d.placeOfSupply = pos;
        d.original = original == null ? "" : original;
        return d;
    }

    private static NoteLine noteLine(String hsn, String sac, String name, String unit, Integer qty,
                                     Double total, Double taxable, Double igst, Double cgst, Double sgst,
                                     Double igstRate, Double cgstRate, Double sgstRate) {
        NoteLine l = new NoteLine();
        l.hsn = !isBlank(hsn) ? hsn : sac;
        l.description = name;
        l.uqc = unit;
        l.quantity = qty == null ? 0 : qty;
        l.totalValue = nz(total);
        l.taxable = nz(taxable);
        l.igst = nz(igst);
        l.cgst = nz(cgst);
        l.sgst = nz(sgst);
        l.rate = nz(igstRate) > 0 ? nz(igstRate) : nz(cgstRate) + nz(sgstRate);
        return l;
    }

    /** A note saved without item lines: its header figures, with no HSN to report against. */
    private static NoteLine headerLine(Double taxable, Double igst, Double cgst, Double sgst,
                                       Double igstRate, Double cgstRate, Double sgstRate, Double total) {
        return noteLine(null, null, null, null, 0, total, taxable, igst, cgst, sgst, igstRate, cgstRate, sgstRate);
    }

    static boolean cancelled(String status) {
        return status != null && "CANCELLED".equalsIgnoreCase(status.trim());
    }

    /**
     * 12 - the HSN-wise summary, aggregated across every invoice in the period.
     *
     * Mandatory in GSTR-1 and previously not produced at all, which alone would have stopped
     * the return being filed from this data.
     */
    public List<Map<String, Object>> hsnSummary(List<GstInvoice> invoices) {
        return hsnSummary(invoices, null);
    }

    /** 12, B2B list: supplies to buyers with a GSTIN. */
    public List<Map<String, Object>> hsnSummaryB2b(List<GstInvoice> invoices) {
        return hsnSummary(invoices, Boolean.TRUE);
    }

    /** 12, B2C list: supplies to buyers without a GSTIN. */
    public List<Map<String, Object>> hsnSummaryB2c(List<GstInvoice> invoices) {
        return hsnSummary(invoices, Boolean.FALSE);
    }

    /** @param registered TRUE for buyers with a GSTIN only, FALSE for the rest, null for all */
    private List<Map<String, Object>> hsnSummary(List<GstInvoice> invoices, Boolean registered) {
        return hsnSummary(invoices, List.of(), registered);
    }

    /**
     * Table 12 net of the period's notes: a credit note takes its quantity, value and tax
     * off the HSN it was raised against, a debit note adds them. Without this a month with
     * returns reports more sales in Table 12 than in the B2B/B2CS tables, and the portal
     * flags the mismatch.
     *
     * A row that nets to nothing - everything sold was returned - is left out.
     *
     * @param registered TRUE for buyers with a GSTIN only, FALSE for the rest, null for all
     */
    public List<Map<String, Object>> hsnSummary(List<GstInvoice> invoices, List<NoteDoc> notes,
                                                Boolean registered) {
        Map<String, Map<String, Object>> rows = invoiceHsnRows(invoices, registered);
        for (NoteDoc note : notes) {
            if (registered != null && registered != note.registered()) continue;
            for (NoteLine line : note.lines) {
                if (isBlank(line.hsn)) continue;
                Map<String, Object> row = hsnRow(rows, line.hsn, line.description, line.uqc, line.rate);
                add(row, "Total Quantity", note.sign * line.quantity);
                add(row, "Total Value", note.sign * line.totalValue);
                add(row, "Taxable Value", note.sign * line.taxable);
                add(row, "Integrated Tax Amount", note.sign * line.igst);
                add(row, "Central Tax Amount", note.sign * line.cgst);
                add(row, "State/UT Tax Amount", note.sign * line.sgst);
            }
        }
        rows.values().removeIf(Gstr1ExportService::allZero);
        return new ArrayList<>(rows.values());
    }

    private static boolean allZero(Map<String, Object> row) {
        for (String k : new String[]{"Total Quantity", "Total Value", "Taxable Value", "Integrated Tax Amount",
                "Central Tax Amount", "State/UT Tax Amount", "Cess Amount"}) {
            if (Math.abs((Double) row.get(k)) >= 0.005) return false;
        }
        return true;
    }

    private static Map<String, Object> hsnRow(Map<String, Map<String, Object>> rows, String hsn,
                                              String description, String unit, double rate) {
        return rows.computeIfAbsent(hsn + "|" + rate, k -> {
            Map<String, Object> fresh = new LinkedHashMap<>();
            fresh.put("HSN", hsn);
            fresh.put("Description", description);
            fresh.put("UQC", uqcLabel(unit));
            fresh.put("Total Quantity", 0.0);
            fresh.put("Total Value", 0.0);
            fresh.put("Taxable Value", 0.0);
            fresh.put("Integrated Tax Amount", 0.0);
            fresh.put("Central Tax Amount", 0.0);
            fresh.put("State/UT Tax Amount", 0.0);
            fresh.put("Cess Amount", 0.0);
            fresh.put("Rate", round(rate));
            return fresh;
        });
    }

    private Map<String, Map<String, Object>> invoiceHsnRows(List<GstInvoice> invoices, Boolean registered) {
        Map<String, Map<String, Object>> byHsnAndRate = new LinkedHashMap<>();
        for (GstInvoice inv : invoices) {
            if (registered != null && registered == isBlank(inv.getBuyerGstin())) continue;
            for (GstInvoiceItem item : inv.getItems()) {
                double rate = nz(item.getIgstRate()) > 0
                        ? nz(item.getIgstRate())
                        : nz(item.getCgstRate()) + nz(item.getSgstRate());
                String hsn = item.getHsnCode() != null ? item.getHsnCode() : item.getSacCode();
                if (isBlank(hsn)) continue;
                Map<String, Object> row = hsnRow(byHsnAndRate, hsn, item.getProductName(),
                        item.getUnitOfMeasure(), rate);
                add(row, "Total Quantity", item.getQuantity() == null ? 0 : item.getQuantity());
                add(row, "Total Value", nz(item.getTotalAmount()));
                add(row, "Taxable Value", nz(item.getTaxableValue()));
                add(row, "Integrated Tax Amount", nz(item.getIgstAmount()));
                add(row, "Central Tax Amount", nz(item.getCgstAmount()));
                add(row, "State/UT Tax Amount", nz(item.getSgstAmount()));
                add(row, "Cess Amount", nz(item.getCessAmount()));
            }
        }
        return byHsnAndRate;
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
            row.put("Note Date", noteDate == null ? "" : noteDate.format(GST_DATE));
            row.put("Note Type", noteType);
            row.put("Place Of Supply", placeOfSupply);
            row.put("Reverse Charge", "N");
            row.put("Note Supply Type", "Regular B2B");
            row.put("Note Value", round(nz(noteValue)));
            row.put("Applicable % of Tax Rate", "");
            row.put("Rate", round(Double.parseDouble(e.getKey())));
            row.put("Taxable Value", round(e.getValue()));
            row.put("Cess Amount", 0.0);
            rows.add(row);
        }
        return rows;
    }

    /**
     * 14 - supplies made through the marketplace, on which it collects TCS.
     *
     * One row for the period: invoices, less credit notes, plus debit notes, so the figure
     * matches what the operator reports against this seller in GSTR-8. Empty when the sales
     * did not go through an operator.
     */
    public List<Map<String, Object>> eco(List<GstInvoice> invoices, List<CreditNote> creditNotes,
                                         List<DebitNote> debitNotes, String ecoGstin, String ecoName) {
        if (ecoGstin == null || invoices.isEmpty()) return List.of();
        double value = 0, igst = 0, cgst = 0, sgst = 0, cess = 0;
        for (GstInvoice inv : invoices) {
            value += nz(inv.getTaxableAmount());
            igst += nz(inv.getIgstAmount());
            cgst += nz(inv.getCgstAmount());
            sgst += nz(inv.getSgstAmount());
            cess += nz(inv.getTotalCess());
        }
        for (CreditNote cn : creditNotes == null ? List.<CreditNote>of() : creditNotes) {
            value -= nz(cn.getTaxableAmount());
            igst -= nz(cn.getIgstAmount());
            cgst -= nz(cn.getCgstAmount());
            sgst -= nz(cn.getSgstAmount());
        }
        for (DebitNote dn : debitNotes == null ? List.<DebitNote>of() : debitNotes) {
            value += nz(dn.getTaxableAmount());
            igst += nz(dn.getIgstAmount());
            cgst += nz(dn.getCgstAmount());
            sgst += nz(dn.getSgstAmount());
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("Nature of Supply", ECO_TCS);
        row.put("GSTIN of E-Commerce Operator", ecoGstin);
        row.put("E-Commerce Operator Name", ecoName == null ? "" : ecoName);
        row.put("Net value of supplies", round(value));
        row.put("Integrated tax", round(igst));
        row.put("Central tax", round(cgst));
        row.put("State/UT tax", round(sgst));
        row.put("Cess", round(cess));
        return List.of(row);
    }

    /** Every section, keyed by the name the utility uses for it. */
    public Map<String, List<Map<String, Object>>> allSections(List<GstInvoice> invoices) {
        return allSections(invoices, List.of(), List.of());
    }

    /** Every section, including credit and debit notes, for a return period from May 2025. */
    public Map<String, List<Map<String, Object>>> allSections(List<GstInvoice> invoices,
                                                              List<CreditNote> creditNotes,
                                                              List<DebitNote> debitNotes) {
        return allSections(invoices, creditNotes, debitNotes, null);
    }

    /**
     * Every section for the period starting {@code periodStart} (null means a current period).
     * The HSN summary comes as hsn_b2b and hsn_b2c; the single "hsn" list is added only for
     * periods before May 2025, when the portal still took one.
     */
    public Map<String, List<Map<String, Object>>> allSections(List<GstInvoice> invoices,
                                                              List<CreditNote> creditNotes,
                                                              List<DebitNote> debitNotes,
                                                              LocalDate periodStart) {
        return allSections(invoices, creditNotes, debitNotes, periodStart, null, null);
    }

    /**
     * As above, for sales made through the marketplace: {@code ecoGstin} is its TCS GSTIN in
     * the seller's state and {@code ecoName} its legal name. Pass null for both otherwise.
     */
    public Map<String, List<Map<String, Object>>> allSections(List<GstInvoice> invoices,
                                                              List<CreditNote> creditNotes,
                                                              List<DebitNote> debitNotes,
                                                              LocalDate periodStart,
                                                              String ecoGstin, String ecoName) {
        return allSections(invoices, creditNotes, debitNotes, periodStart, ecoGstin, ecoName, Set.of());
    }

    /**
     * As above. {@code earlierB2clInvoices} names invoices from earlier periods that were
     * reported in B2CL, so a note raised against one now goes to CDNUR; B2CL invoices in
     * this period are recognised without it.
     *
     * Cancelled invoices and notes are left out of every table: a cancelled invoice is a
     * supply that never happened.
     */
    public Map<String, List<Map<String, Object>>> allSections(List<GstInvoice> invoices,
                                                              List<CreditNote> creditNotes,
                                                              List<DebitNote> debitNotes,
                                                              LocalDate periodStart,
                                                              String ecoGstin, String ecoName,
                                                              Set<String> earlierB2clInvoices) {
        List<GstInvoice> live = new ArrayList<>();
        for (GstInvoice inv : invoices) if (!cancelled(inv.getStatus())) live.add(inv);
        List<CreditNote> liveCn = new ArrayList<>();
        for (CreditNote cn : creditNotes == null ? List.<CreditNote>of() : creditNotes) {
            if (!cancelled(cn.getStatus())) liveCn.add(cn);
        }
        List<DebitNote> liveDn = new ArrayList<>();
        for (DebitNote dn : debitNotes == null ? List.<DebitNote>of() : debitNotes) {
            if (!cancelled(dn.getStatus())) liveDn.add(dn);
        }
        List<NoteDoc> notes = notes(liveCn, liveDn);

        Set<String> b2clOriginals = new HashSet<>(earlierB2clInvoices == null ? Set.of() : earlierB2clInvoices);
        for (GstInvoice inv : live) {
            if (isBlank(inv.getBuyerGstin())
                    && GstComplianceUtil.isB2cLarge(inv.getIsInterState(), inv.getTaxableAmount())) {
                b2clOriginals.add(inv.getInvoiceNumber());
            }
        }

        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        out.put("b2b", b2b(live, ecoGstin));
        out.put("b2cl", b2cl(live, ecoGstin));
        out.put("b2cs", b2cs(live, ecoGstin, notes, b2clOriginals));
        out.put("cdn", cdn(liveCn, liveDn));
        out.put("cdnur", cdnur(notes, b2clOriginals));
        out.put("hsn_b2b", hsnSummary(live, notes, Boolean.TRUE));
        out.put("hsn_b2c", hsnSummary(live, notes, Boolean.FALSE));
        if (periodStart != null && periodStart.isBefore(HSN_SPLIT_FROM)) {
            out.put("hsn", hsnSummary(live, notes, null));
        }
        out.put("eco", eco(live, liveCn, liveDn, ecoGstin, ecoName));
        return out;
    }

    public String[] headersFor(String section) {
        switch (section == null ? "" : section.toLowerCase()) {
            case "b2b": return B2B_HEADERS;
            case "b2cl": return B2CL_HEADERS;
            case "b2cs": return B2CS_HEADERS;
            case "cdn": return CDN_HEADERS;
            case "cdnur": return CDNUR_HEADERS;
            case "hsn":
            case "hsn_b2b":
            case "hsn_b2c": return HSN_HEADERS;
            case "eco": return ECO_HEADERS;
            default: throw new IllegalArgumentException("Unknown GSTR-1 section '" + section
                    + "'. Use b2b, b2cl, b2cs, cdn, cdnur, hsn_b2b, hsn_b2c or eco.");
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
