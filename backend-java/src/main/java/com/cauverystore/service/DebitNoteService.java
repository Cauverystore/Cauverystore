package com.cauverystore.service;

import com.cauverystore.entities.*;
import com.cauverystore.repository.*;
import com.cauverystore.util.PdfBrandingUtil;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class DebitNoteService {

    private final DebitNoteRepository debitNoteRepo;
    private final DebitNoteItemRepository debitNoteItemRepo;
    private final GstInvoiceRepository invoiceRepo;
    private final GstInvoiceItemRepository invoiceItemRepo;
    private final AuditService auditService;

    public DebitNoteService(DebitNoteRepository debitNoteRepo, DebitNoteItemRepository debitNoteItemRepo,
                            GstInvoiceRepository invoiceRepo, GstInvoiceItemRepository invoiceItemRepo,
                            AuditService auditService) {
        this.debitNoteRepo = debitNoteRepo;
        this.debitNoteItemRepo = debitNoteItemRepo;
        this.invoiceRepo = invoiceRepo;
        this.invoiceItemRepo = invoiceItemRepo;
        this.auditService = auditService;
    }

    /** Manual debit note against an existing invoice for additional charges (positive tax adjustment). */
    @Transactional
    public Map<String, Object> generateManualDebitNote(Long invoiceId, Double amount, String reason, Long userId) {
        GstInvoice inv = invoiceRepo.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + invoiceId));

        double total = inv.getTotalAmount() != null ? inv.getTotalAmount() : 0;
        double factor = 1.0;
        if (amount != null) {
            if (amount <= 0) throw new RuntimeException("Debit note amount must be positive");
            if (amount > total) throw new RuntimeException("Debit note amount cannot exceed invoice total " + total);
            factor = total > 0 ? amount / total : 0;
        }

        DebitNote dn = new DebitNote();
        dn.setOrderId(inv.getOrderId());
        dn.setInvoiceId(inv.getId());
        dn.setOriginalInvoiceNumber(inv.getInvoiceNumber());
        dn.setReferenceType("INVOICE");
        dn.setReferenceId(inv.getId());
        dn.setNoteType("DEBIT");
        dn.setReason(reason != null && !reason.isBlank() ? reason : "Additional charge against invoice " + inv.getInvoiceNumber());
        dn.setDebitNoteDate(LocalDate.now());
        dn.setSellerId(inv.getSellerId());
        dn.setCustomerId(inv.getCustomerId());
        dn.setCustomerEmail(inv.getCustomerEmail());
        dn.setSellerGstin(inv.getSellerGstin());
        dn.setSellerLegalName(inv.getSellerLegalName());
        dn.setSellerAddress(inv.getSellerAddress());
        dn.setBuyerGstin(inv.getBuyerGstin());
        dn.setBuyerName(inv.getBuyerName());
        dn.setBuyerAddress(inv.getBuyerAddress());
        dn.setBuyerStateCode(inv.getBuyerStateCode());
        dn.setPlaceOfSupply(inv.getPlaceOfSupply());
        dn.setIsInterState(inv.getIsInterState());
        dn.setUtgstApplied(Boolean.TRUE.equals(inv.getUtgstApplied()));
        dn.setInvoiceType(inv.getInvoiceType() != null ? inv.getInvoiceType() : "B2C");

        dn.setTaxableAmount(round(inv.getTaxableAmount() * factor));
        dn.setCgstRate(inv.getCgstRate());
        dn.setSgstRate(inv.getSgstRate());
        dn.setIgstRate(inv.getIgstRate());
        dn.setCgstAmount(round(inv.getCgstAmount() * factor));
        dn.setSgstAmount(round(inv.getSgstAmount() * factor));
        dn.setIgstAmount(round(inv.getIgstAmount() * factor));
        dn.setTotalTax(round(nz(dn.getCgstAmount()) + nz(dn.getSgstAmount()) + nz(dn.getIgstAmount())));
        dn.setTcsAmount(round(inv.getTcsAmount() * factor));
        dn.setTotalAmount(round(nz(dn.getTaxableAmount()) + nz(dn.getTotalTax()) + nz(dn.getTcsAmount())));
        dn.setItems(buildItemsFromInvoice(dn, inv, factor));

        dn.setDebitNoteNumber(generateDebitNoteNumber("DN"));
        dn.setStatus("GENERATED");
        DebitNote saved = debitNoteRepo.save(dn);

        auditService.log(userId, userId != null ? "seller:" + userId : "system", "DEBIT_NOTE_GENERATED",
                "DebitNote", saved.getId(),
                "Debit note " + saved.getDebitNoteNumber() + " against invoice " + inv.getInvoiceNumber(), null);

        return Map.of("debitNote", saved, "message", "Debit note generated successfully");
    }

    public Map<String, Object> getDebitNoteById(Long id, Long userId, String userRole) {
        DebitNote dn = debitNoteRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Debit note not found"));
        checkDebitNoteOwnership(dn, userId, userRole);
        dn.setItems(debitNoteItemRepo.findByDebitNoteId(id));
        return Map.of("debitNote", dn);
    }

    public Map<String, Object> listDebitNotes(Long userId, String userRole, int page, int size) {
        Page<DebitNote> result;
        if ("ADMIN".equals(userRole) || "SUPER_ADMIN".equals(userRole)) {
            result = debitNoteRepo.findAll(PageRequest.of(page, size));
        } else if ("SELLER".equals(userRole)) {
            result = debitNoteRepo.findBySellerIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        } else {
            result = debitNoteRepo.findByCustomerIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        }
        for (DebitNote dn : result.getContent()) {
            dn.setItems(debitNoteItemRepo.findByDebitNoteId(dn.getId()));
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("content", result.getContent());
        resp.put("totalPages", result.getTotalPages());
        resp.put("totalElements", result.getTotalElements());
        resp.put("page", page);
        resp.put("size", size);
        return resp;
    }

    private void checkDebitNoteOwnership(DebitNote dn, Long userId, String userRole) {
        if (userRole == null) userRole = "CUSTOMER";
        String role = userRole.toUpperCase();
        if ("ADMIN".equals(role) || "SUPER_ADMIN".equals(role)) return;
        if ("SELLER".equals(role) && userId.equals(dn.getSellerId())) return;
        if ("CUSTOMER".equals(role) && userId.equals(dn.getCustomerId())) return;
        throw new RuntimeException("Access denied: you do not have permission to view this debit note");
    }

    public byte[] generateDebitNotePdf(Long id) throws DocumentException {
        DebitNote dn = debitNoteRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Debit note not found: " + id));
        dn.setItems(debitNoteItemRepo.findByDebitNoteId(id));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        Font bold14 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        Font bold12 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
        Font bold10 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font normal9 = FontFactory.getFont(FontFactory.HELVETICA, 9);
        Font bold9 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        Font small8 = FontFactory.getFont(FontFactory.HELVETICA, 8);

        boolean interState = Boolean.TRUE.equals(dn.getIsInterState());
        boolean utgst = Boolean.TRUE.equals(dn.getUtgstApplied());
        String stateLabel = utgst ? "UTGST" : "SGST";

        doc.add(PdfBrandingUtil.buildBrandHeader());
        doc.add(PdfBrandingUtil.buildDivider());
        doc.add(new Paragraph(" "));

        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{60, 40});
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.addElement(new Paragraph("Debit Note", bold14));
        leftCell.addElement(new Paragraph(
                "Original for Recipient" + (Boolean.TRUE.equals(dn.getIsInterState()) ? " (Inter-State)" : " (Intra-State)"),
                small8));
        if (dn.getReason() != null) {
            leftCell.addElement(new Paragraph("Reason: " + dn.getReason(), small8));
        }
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(new Paragraph("Debit Note No.", small8));
        rightCell.addElement(new Paragraph(safeStr(dn.getDebitNoteNumber()), bold12));
        rightCell.addElement(new Paragraph("Date", small8));
        rightCell.addElement(new Paragraph(dn.getDebitNoteDate() != null ? dn.getDebitNoteDate().toString() : "", normal9));
        if (dn.getOriginalInvoiceNumber() != null) {
            rightCell.addElement(new Paragraph("Against Invoice: " + dn.getOriginalInvoiceNumber(), small8));
        }
        headerTable.addCell(leftCell);
        headerTable.addCell(rightCell);
        doc.add(headerTable);
        doc.add(new Paragraph(" "));

        PdfPTable parties = new PdfPTable(2);
        parties.setWidthPercentage(100);
        parties.setWidths(new float[]{50, 50});
        PdfPCell sellCell = new PdfPCell();
        sellCell.setPadding(8);
        sellCell.addElement(new Paragraph("Seller (Supplier)", bold10));
        sellCell.addElement(new Paragraph(safeStr(dn.getSellerLegalName()), normal9));
        sellCell.addElement(new Paragraph("GSTIN: " + safeStr(dn.getSellerGstin()), normal9));
        sellCell.addElement(new Paragraph("Address: " + safeStr(dn.getSellerAddress()), small8));
        parties.addCell(sellCell);

        PdfPCell buyCell = new PdfPCell();
        buyCell.setPadding(8);
        buyCell.addElement(new Paragraph("Buyer / Recipient", bold10));
        buyCell.addElement(new Paragraph(safeStr(dn.getBuyerName()), normal9));
        buyCell.addElement(new Paragraph("GSTIN: " + (dn.getBuyerGstin() != null ? dn.getBuyerGstin() : "URP"), normal9));
        buyCell.addElement(new Paragraph("Address: " + safeStr(dn.getBuyerAddress()), small8));
        parties.addCell(buyCell);
        doc.add(parties);
        doc.add(new Paragraph(" "));

        int numColumns = Boolean.TRUE.equals(dn.getIsInterState()) ? 8 : 9;
        PdfPTable table = new PdfPTable(numColumns);
        table.setWidthPercentage(100);
        float[] colWidths = Boolean.TRUE.equals(dn.getIsInterState())
                ? new float[]{4, 10, 26, 7, 10, 13, 13, 17}
                : new float[]{4, 9, 22, 6, 9, 12, 11, 11, 16};
        table.setWidths(colWidths);
        String[] hdrs;
        if (interState) {
            hdrs = new String[]{"#", "HSN/SAC", "Description", "Qty", "Unit Price", "Taxable Value", "IGST", "Total"};
        } else {
            hdrs = new String[]{"#", "HSN/SAC", "Description", "Qty", "Unit Price", "Taxable Value", "CGST", stateLabel, "Total"};
        }
        for (String h : hdrs) {
            PdfPCell hc = new PdfPCell(new Phrase(h, bold9));
            hc.setBackgroundColor(PdfBrandingUtil.TEAL);
            hc.setPadding(4);
            hc.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(hc);
        }
        int sn = 1;
        for (DebitNoteItem item : dn.getItems()) {
            table.addCell(new Phrase(String.valueOf(sn++), normal9));
            table.addCell(new Phrase(safeStr(item.getHsnCode()), small8));
            table.addCell(new Phrase(safeStr(item.getProductName()), normal9));
            table.addCell(new Phrase(String.valueOf(item.getQuantity()), normal9));
            table.addCell(new Phrase("\u20B9" + String.format("%.2f", nz(item.getUnitPrice())), normal9));
            table.addCell(new Phrase("\u20B9" + String.format("%.2f", nz(item.getTaxableValue())), normal9));
            if (Boolean.TRUE.equals(dn.getIsInterState())) {
                table.addCell(new Phrase((nz(item.getIgstRate())) + "% / \u20B9" + String.format("%.2f", nz(item.getIgstAmount())), small8));
            } else {
                table.addCell(new Phrase((nz(item.getCgstRate())) + "% / \u20B9" + String.format("%.2f", nz(item.getCgstAmount())), small8));
                table.addCell(new Phrase((nz(item.getSgstRate())) + "% / \u20B9" + String.format("%.2f", nz(item.getSgstAmount())), small8));
            }
            table.addCell(new Phrase("\u20B9" + String.format("%.2f", nz(item.getTotalAmount())), bold9));
        }
        doc.add(table);
        doc.add(new Paragraph(" "));

        PdfPTable sumTable = new PdfPTable(2);
        sumTable.setWidthPercentage(40);
        sumTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        addSumRow(sumTable, "Taxable Amount", "\u20B9" + String.format("%.2f", nz(dn.getTaxableAmount())), normal9);
        if (interState) {
            addSumRow(sumTable, "IGST", "\u20B9" + String.format("%.2f", nz(dn.getIgstAmount())), normal9);
        } else {
            addSumRow(sumTable, "CGST", "\u20B9" + String.format("%.2f", nz(dn.getCgstAmount())), normal9);
            addSumRow(sumTable, stateLabel, "\u20B9" + String.format("%.2f", nz(dn.getSgstAmount())), normal9);
        }
        addSumRow(sumTable, "Total Tax", "\u20B9" + String.format("%.2f", nz(dn.getTotalTax())), normal9);
        if (nz(dn.getTcsAmount()) > 0) {
            addSumRow(sumTable, "TCS", "\u20B9" + String.format("%.2f", nz(dn.getTcsAmount())), normal9);
        }
        PdfPCell totalCell = new PdfPCell(new Phrase("Total Amount Payable", bold12));
        totalCell.setBorder(Rectangle.TOP);
        sumTable.addCell(totalCell);
        PdfPCell totalVal = new PdfPCell(new Phrase("\u20B9" + String.format("%.2f", nz(dn.getTotalAmount())), bold12));
        totalVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalVal.setBorder(Rectangle.TOP);
        sumTable.addCell(totalVal);
        doc.add(sumTable);

        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Amount in Words: " + numberToWordsPdf(nz(dn.getTotalAmount())), bold9));
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Declaration: This debit note documents an additional tax charge on the original supply and is reported in GSTR-1 / GSTR-3B for the period in which it is issued.", small8));

        doc.close();
        return baos.toByteArray();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private List<DebitNoteItem> buildItemsFromInvoice(DebitNote dn, GstInvoice inv, double factor) {
        List<DebitNoteItem> list = new ArrayList<>();
        for (GstInvoiceItem s : invoiceItemRepo.findByInvoiceId(inv.getId())) {
            if (s.getQuantity() == null || s.getQuantity() == 0) continue;
            DebitNoteItem it = new DebitNoteItem();
            it.setDebitNote(dn);
            it.setProductName(s.getProductName());
            it.setHsnCode(s.getHsnCode());
            it.setSacCode(s.getSacCode());
            it.setUnitOfMeasure(s.getUnitOfMeasure() != null ? s.getUnitOfMeasure() : "NOS");
            it.setQuantity(s.getQuantity());
            it.setUnitPrice(s.getUnitPrice());
            it.setTaxableValue(round(nz(s.getTaxableValue()) * factor));
            it.setCgstRate(s.getCgstRate());
            it.setCgstAmount(round(nz(s.getCgstAmount()) * factor));
            it.setSgstRate(s.getSgstRate());
            it.setSgstAmount(round(nz(s.getSgstAmount()) * factor));
            it.setIgstRate(s.getIgstRate());
            it.setIgstAmount(round(nz(s.getIgstAmount()) * factor));
            it.setTotalAmount(round(nz(it.getTaxableValue()) + nz(it.getCgstAmount())
                    + nz(it.getSgstAmount()) + nz(it.getIgstAmount())));
            list.add(it);
        }
        return list;
    }

    private String generateDebitNoteNumber(String prefix) {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM"));
        String safePrefix = prefix != null ? prefix.trim().toUpperCase().replaceAll("[^A-Z0-9]", "") : "DN";
        if (safePrefix.isEmpty()) safePrefix = "DN";
        if (safePrefix.length() > 9) safePrefix = safePrefix.substring(0, 9);
        String prefixPattern = safePrefix + datePart + "/";
        String maxNum = debitNoteRepo.findMaxDebitNoteNumberByPrefix(prefixPattern);
        int seq = 1;
        if (maxNum != null) {
            String[] parts = maxNum.split("/");
            seq = Integer.parseInt(parts[parts.length - 1]) + 1;
        }
        return prefixPattern + String.format("%05d", seq);
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private double nz(Double v) {
        return v != null ? v : 0.0;
    }

    private String safeStr(String s) {
        return s != null ? s : "";
    }

    private void addSumRow(PdfPTable table, String label, String value, Font font) {
        table.addCell(new Phrase(label, font));
        Phrase v = new Phrase(value, font);
        PdfPCell c = new PdfPCell(v);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(c);
    }

    private String numberToWordsPdf(double num) {
        if (num <= 0) return "Zero Rupees Only";
        String[] a = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
                "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
        String[] b = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};
        java.util.function.Function<Long, String> fn = new java.util.function.Function<Long, String>() {
            @Override
            public String apply(Long n) {
                if (n < 20) return a[n.intValue()];
                if (n < 100) return b[n.intValue() / 10] + (n % 10 > 0 ? " " + a[n.intValue() % 10] : "");
                if (n < 1000) return a[n.intValue() / 100] + " Hundred" + (n % 100 > 0 ? " " + apply(n % 100) : "");
                if (n < 100000) return apply(n / 1000) + " Thousand" + (n % 1000 > 0 ? " " + apply(n % 1000) : "");
                if (n < 10000000) return apply(n / 100000) + " Lakh" + (n % 100000 > 0 ? " " + apply(n % 100000) : "");
                return apply(n / 10000000) + " Crore" + (n % 10000000 > 0 ? " " + apply(n % 10000000) : "");
            }
        };
        long whole = (long) Math.floor(num);
        long decimal = Math.round((num - whole) * 100);
        String result = fn.apply(whole) + " Rupees";
        if (decimal > 0) result += " and " + fn.apply(decimal) + " Paise";
        return result + " Only";
    }
}
