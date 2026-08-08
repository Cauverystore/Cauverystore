package com.cauverystore.service;

import com.cauverystore.entities.*;
import com.cauverystore.repository.*;
import com.cauverystore.util.GstComplianceUtil;
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
public class CreditNoteService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(CreditNoteService.class);

    private final CreditNoteRepository creditNoteRepo;
    private final CreditNoteItemRepository creditNoteItemRepo;
    private final GstInvoiceRepository invoiceRepo;
    private final GstInvoiceItemRepository invoiceItemRepo;
    private final OrderRepository orderRepo;
    private final ReturnRequestRepository returnRepo;
    private final SellerRegistrationRepository sellerRegRepo;
    private final GstConfigurationRepository configRepo;
    private final UserRepository userRepo;
    private final AuditService auditService;

    private static final Map<String, String> STATE_CODES = new LinkedHashMap<>();
    static {
        STATE_CODES.put("35", "Andaman and Nicobar"); STATE_CODES.put("28", "Andhra Pradesh");
        STATE_CODES.put("12", "Arunachal Pradesh"); STATE_CODES.put("18", "Assam");
        STATE_CODES.put("10", "Bihar"); STATE_CODES.put("04", "Chandigarh");
        STATE_CODES.put("22", "Chhattisgarh"); STATE_CODES.put("26", "Dadra and Nagar Haveli and Daman and Diu");
        STATE_CODES.put("07", "Delhi"); STATE_CODES.put("30", "Goa");
        STATE_CODES.put("24", "Gujarat"); STATE_CODES.put("06", "Haryana");
        STATE_CODES.put("02", "Himachal Pradesh"); STATE_CODES.put("01", "Jammu and Kashmir");
        STATE_CODES.put("20", "Jharkhand"); STATE_CODES.put("29", "Karnataka");
        STATE_CODES.put("32", "Kerala"); STATE_CODES.put("31", "Lakshadweep");
        STATE_CODES.put("23", "Madhya Pradesh"); STATE_CODES.put("27", "Maharashtra");
        STATE_CODES.put("14", "Manipur"); STATE_CODES.put("17", "Meghalaya");
        STATE_CODES.put("15", "Mizoram"); STATE_CODES.put("13", "Nagaland");
        STATE_CODES.put("21", "Odisha"); STATE_CODES.put("34", "Puducherry");
        STATE_CODES.put("03", "Punjab"); STATE_CODES.put("08", "Rajasthan");
        STATE_CODES.put("11", "Sikkim"); STATE_CODES.put("33", "Tamil Nadu");
        STATE_CODES.put("36", "Telangana"); STATE_CODES.put("16", "Tripura");
        STATE_CODES.put("09", "Uttar Pradesh"); STATE_CODES.put("05", "Uttarakhand");
        STATE_CODES.put("19", "West Bengal");
    }

    private final TcsRecordRepository tcsRepo;

    public CreditNoteService(CreditNoteRepository creditNoteRepo, CreditNoteItemRepository creditNoteItemRepo,
                             GstInvoiceRepository invoiceRepo, GstInvoiceItemRepository invoiceItemRepo,
                             OrderRepository orderRepo, ReturnRequestRepository returnRepo,
                             SellerRegistrationRepository sellerRegRepo, GstConfigurationRepository configRepo,
                             UserRepository userRepo, AuditService auditService,
                             TcsRecordRepository tcsRepo) {
        this.creditNoteRepo = creditNoteRepo;
        this.tcsRepo = tcsRepo;
        this.creditNoteItemRepo = creditNoteItemRepo;
        this.invoiceRepo = invoiceRepo;
        this.invoiceItemRepo = invoiceItemRepo;
        this.orderRepo = orderRepo;
        this.returnRepo = returnRepo;
        this.sellerRegRepo = sellerRegRepo;
        this.configRepo = configRepo;
        this.userRepo = userRepo;
        this.auditService = auditService;
    }

    /** Full tax reversal for a cancelled/refunded order. Mirrors the original invoice if one exists. */
    @Transactional
    public Map<String, Object> generateFullCreditNote(Long orderId, String reason, String actorEmail) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        Optional<CreditNote> existing = creditNoteRepo.findByOrderIdAndNoteType(orderId, "CREDIT");
        if (existing.isPresent()) {
            return Map.of("creditNote", existing.get(), "message", "Credit note already exists for this order");
        }
        GstInvoice inv = invoiceRepo.findByOrderId(orderId).orElse(null);

        CreditNote cn = new CreditNote();
        cn.setOrderId(orderId);
        cn.setReferenceType("ORDER");
        cn.setReferenceId(orderId);
        cn.setNoteType("CREDIT");
        cn.setReason(reason != null && !reason.isBlank() ? reason : "Cancellation / refund of order");
        cn.setCreditNoteDate(LocalDate.now());

        applySellerDetails(cn, order, inv);
        applyBuyerDetails(cn, order, inv);

        boolean isInterState = Boolean.TRUE.equals(cn.getIsInterState());

        if (inv != null) {
            // Reverse the original invoice fully (tax reversal).
            double factor = 1.0;
            cn.setInvoiceId(inv.getId());
            cn.setOriginalInvoiceNumber(inv.getInvoiceNumber());
            cn.setInvoiceType(inv.getInvoiceType() != null ? inv.getInvoiceType() : "B2C");
            cn.setUtgstApplied(Boolean.TRUE.equals(inv.getUtgstApplied()));
            cn.setTaxableAmount(round(inv.getTaxableAmount() * factor));
            cn.setCgstRate(inv.getCgstRate());
            cn.setSgstRate(inv.getSgstRate());
            cn.setIgstRate(inv.getIgstRate());
            cn.setCgstAmount(round(inv.getCgstAmount() * factor));
            cn.setSgstAmount(round(inv.getSgstAmount() * factor));
            cn.setIgstAmount(round(inv.getIgstAmount() * factor));
            cn.setTotalTax(round((cn.getCgstAmount() + cn.getSgstAmount() + cn.getIgstAmount())));
            cn.setTcsAmount(round(inv.getTcsAmount() * factor));
            cn.setTotalAmount(round(cn.getTaxableAmount() + cn.getTotalTax() + cn.getTcsAmount()));
            cn.setItems(buildItemsFromInvoice(cn, inv, factor));
        } else {
            // No GST invoice was ever raised - compute reversal from the order itself.
            cn.setInvoiceType("B2C");
            List<CreditNoteItem> items = buildItemsFromOrder(cn, order);
            double taxable = 0, cgst = 0, sgst = 0, igst = 0;
            for (CreditNoteItem it : items) {
                taxable += it.getTaxableValue();
                cgst += it.getCgstAmount();
                sgst += it.getSgstAmount();
                igst += it.getIgstAmount();
            }
            cn.setTaxableAmount(round(taxable));
            cn.setCgstAmount(round(cgst));
            cn.setSgstAmount(round(sgst));
            cn.setIgstAmount(round(igst));
            cn.setCgstRate(!items.isEmpty() && items.get(0).getCgstRate() != null ? items.get(0).getCgstRate() : 0.0);
            cn.setSgstRate(!isInterState ? cn.getCgstRate() : 0.0);
            cn.setIgstRate(!items.isEmpty() && items.get(0).getIgstRate() != null ? items.get(0).getIgstRate() : 0.0);
            cn.setTotalTax(round(cgst + sgst + igst));
            cn.setTcsAmount(0.0);
            cn.setTotalAmount(round(taxable + cn.getTotalTax()));
            cn.setItems(items);
        }

        cn.setCreditNoteNumber(generateCreditNoteNumber("CN"));
        cn.setStatus("GENERATED");
        CreditNote saved = creditNoteRepo.save(cn);
        writeTcsReversal(saved, saved.getOrderId());

        auditService.log(null, actorEmail != null ? actorEmail : "system", "CREDIT_NOTE_GENERATED",
                "CreditNote", saved.getId(),
                "Credit note " + saved.getCreditNoteNumber() + " for order " + orderId
                        + (inv != null ? " against invoice " + inv.getInvoiceNumber() : ""), null);

        return Map.of("creditNote", saved, "message", "Credit note generated successfully");
    }

    /**
     * Writes the TCS reversal for a credit note.
     *
     * Section 52 charges TCS on NET monthly supplies, so a return has to reduce the month it
     * falls in. Recording the credit note's TCS on the note alone - which is all that used to
     * happen - leaves tcs_records reporting money that has been refunded, and GSTR-8 is filed
     * from that table.
     *
     * The reversal is a negative row naming the collection it cancels, rather than an edit to
     * that collection: a filed period is a statement about a date and is never recomputed, so a
     * return arriving after filing has to land in the current period instead.
     *
     * Never allowed to fail the credit note. A missing reversal is a reconcilable gap; a credit
     * note that would not save leaves the customer refunded with no document behind it.
     */
    private void writeTcsReversal(CreditNote cn, Long orderId) {
        try {
            if (cn.getTcsAmount() == null || cn.getTcsAmount() <= 0) return;
            if (tcsRepo.findByCreditNoteId(cn.getId()).isPresent()) return;   // already reversed

            TcsRecord collection = tcsRepo
                    .findByOrderIdAndEntryType(orderId, TcsRecord.ENTRY_COLLECTION)
                    .orElse(null);
            if (collection == null) {
                log.warn("Credit note {} carries TCS of {} but no collection row exists for order "
                                + "{} - nothing to reverse.",
                        cn.getCreditNoteNumber(), cn.getTcsAmount(), orderId);
                return;
            }

            TcsRecord reversal = new TcsRecord();
            reversal.setEntryType(TcsRecord.ENTRY_REVERSAL);
            reversal.setReversesId(collection.getId());
            reversal.setCreditNoteId(cn.getId());
            reversal.setOrderId(orderId);
            reversal.setInvoiceNumber(cn.getOriginalInvoiceNumber());
            reversal.setSellerId(collection.getSellerId());
            reversal.setSellerGstin(collection.getSellerGstin());
            reversal.setCustomerId(collection.getCustomerId());
            reversal.setCustomerEmail(collection.getCustomerEmail());
            reversal.setCustomerGstin(collection.getCustomerGstin());
            // Carried from the collection, not recomputed. A reversal has to net against the row
            // it cancels, and GSTR-8 buckets by place of supply and by whether the customer was
            // registered - so a reversal landing in a different bucket would leave both wrong.
            reversal.setPlaceOfSupplyState(collection.getPlaceOfSupplyState());
            reversal.setCustomerRegistered(collection.getCustomerRegistered());
            reversal.setInterState(collection.getInterState());
            reversal.setTcsRate(collection.getTcsRate());
            // Negative, so summing the period nets to what was actually collected.
            reversal.setTcsAmount(-round(cn.getTcsAmount()));
            reversal.setTaxableAmount(-round(nz(cn.getTaxableAmount())));
            // Dated today, not on the original supply: a period already filed is never restated.
            reversal.setTransactionDate(LocalDate.now());
            reversal.setPeriod(currentTcsPeriod());
            reversal.setFilingStatus("PENDING");
            reversal.setRemarks("Reversal for credit note " + cn.getCreditNoteNumber()
                    + " against invoice " + cn.getOriginalInvoiceNumber());
            tcsRepo.save(reversal);

            log.info("TCS reversal of {} written for credit note {} (order {}).",
                    reversal.getTcsAmount(), cn.getCreditNoteNumber(), orderId);
        } catch (Exception e) {
            log.error("Could not write the TCS reversal for credit note {}: {}",
                    cn.getCreditNoteNumber(), e.getMessage(), e);
        }
    }

    /** MMYYYY, the period GSTR-8 is filed for. */
    private String currentTcsPeriod() {
        return LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("MMyyyy"));
    }

    /** Partial tax reversal for an approved/refunded return request. */
    @Transactional
    public Map<String, Object> generateReturnCreditNote(Long returnRequestId, String actorEmail) {
        ReturnRequest rr = returnRepo.findById(returnRequestId)
                .orElseThrow(() -> new RuntimeException("Return request not found: " + returnRequestId));
        Optional<CreditNote> existing = creditNoteRepo.findByReferenceTypeAndReferenceId("RETURN", returnRequestId);
        if (existing.isPresent()) {
            return Map.of("creditNote", existing.get(), "message", "Credit note already exists for this return");
        }
        Order order = rr.getOrder();
        if (order == null) throw new RuntimeException("Return request has no linked order");
        GstInvoice inv = invoiceRepo.findByOrderId(order.getId()).orElse(null);

        CreditNote cn = new CreditNote();
        cn.setOrderId(order.getId());
        cn.setReferenceType("RETURN");
        cn.setReferenceId(returnRequestId);
        cn.setNoteType("CREDIT");
        cn.setReason(rr.getReason() != null && !rr.getReason().isBlank() ? rr.getReason() : "Return of goods");
        cn.setCreditNoteDate(LocalDate.now());

        applySellerDetails(cn, order, inv);
        applyBuyerDetails(cn, order, inv);

        int returnedQty = rr.getQuantity() != null ? rr.getQuantity() : 1;
        if (inv != null) {
            cn.setInvoiceId(inv.getId());
            cn.setOriginalInvoiceNumber(inv.getInvoiceNumber());
            cn.setInvoiceType(inv.getInvoiceType() != null ? inv.getInvoiceType() : "B2C");
        } else {
            cn.setInvoiceType("B2C");
        }
        // A return reverses whatever tax the original carried; if that was UTGST, say so.
        cn.setUtgstApplied(inv != null && Boolean.TRUE.equals(inv.getUtgstApplied()));

        List<CreditNoteItem> items;
        if (inv != null) {
            items = buildItemsForReturn(cn, inv, rr, returnedQty);
        } else if (rr.getOrderItem() != null) {
            items = buildItemsFromOrderItem(cn, rr.getOrderItem(), returnedQty, order);
        } else {
            items = new ArrayList<>();
        }

        double taxable = 0, cgst = 0, sgst = 0, igst = 0;
        for (CreditNoteItem it : items) {
            taxable += it.getTaxableValue();
            cgst += it.getCgstAmount();
            sgst += it.getSgstAmount();
            igst += it.getIgstAmount();
        }
        boolean isInter = Boolean.TRUE.equals(cn.getIsInterState());
        cn.setTaxableAmount(round(taxable));
        cn.setCgstAmount(round(cgst));
        cn.setSgstAmount(round(sgst));
        cn.setIgstAmount(round(igst));
        cn.setCgstRate(!items.isEmpty() && items.get(0).getCgstRate() != null ? items.get(0).getCgstRate() : 0.0);
        cn.setSgstRate(!isInter ? cn.getCgstRate() : 0.0);
        cn.setIgstRate(!items.isEmpty() && items.get(0).getIgstRate() != null ? items.get(0).getIgstRate() : 0.0);
        cn.setTotalTax(round(cgst + sgst + igst));
        cn.setTcsAmount(0.0);
        cn.setTotalAmount(round(taxable + cn.getTotalTax()));
        cn.setItems(items);

        cn.setCreditNoteNumber(generateCreditNoteNumber("CN"));
        cn.setStatus("GENERATED");
        CreditNote saved = creditNoteRepo.save(cn);
        writeTcsReversal(saved, saved.getOrderId());

        auditService.log(null, actorEmail != null ? actorEmail : "system", "CREDIT_NOTE_GENERATED",
                "CreditNote", saved.getId(),
                "Credit note " + saved.getCreditNoteNumber() + " for return request " + returnRequestId
                        + " (qty " + returnedQty + ")", null);

        return Map.of("creditNote", saved, "message", "Credit note generated successfully");
    }

    /** Manual credit note against an existing invoice (full reversal, or proportional if amount provided). */
    @Transactional
    public Map<String, Object> generateManualCreditNote(Long invoiceId, Double amount, String reason, Long userId) {
        GstInvoice inv = invoiceRepo.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + invoiceId));
        Optional<CreditNote> existing = creditNoteRepo.findByOrderIdAndNoteType(inv.getOrderId(), "CREDIT");
        if (existing.isPresent()) {
            return Map.of("creditNote", existing.get(), "message", "Credit note already exists for this order");
        }

        double total = inv.getTotalAmount() != null ? inv.getTotalAmount() : 0;
        double factor = 1.0;
        if (amount != null) {
            if (amount <= 0) throw new RuntimeException("Credit note amount must be positive");
            if (amount > total) throw new RuntimeException("Credit note amount cannot exceed invoice total " + total);
            factor = total > 0 ? amount / total : 0;
        }

        CreditNote cn = new CreditNote();
        cn.setOrderId(inv.getOrderId());
        cn.setInvoiceId(inv.getId());
        cn.setOriginalInvoiceNumber(inv.getInvoiceNumber());
        cn.setReferenceType("INVOICE");
        cn.setReferenceId(inv.getId());
        cn.setNoteType("CREDIT");
        cn.setReason(reason != null && !reason.isBlank() ? reason : "Manual credit against invoice " + inv.getInvoiceNumber());
        cn.setCreditNoteDate(LocalDate.now());
        cn.setSellerId(inv.getSellerId());
        cn.setCustomerId(inv.getCustomerId());
        cn.setCustomerEmail(inv.getCustomerEmail());
        cn.setSellerGstin(inv.getSellerGstin());
        cn.setSellerLegalName(inv.getSellerLegalName());
        cn.setSellerAddress(inv.getSellerAddress());
        cn.setBuyerGstin(inv.getBuyerGstin());
        cn.setBuyerName(inv.getBuyerName());
        cn.setBuyerAddress(inv.getBuyerAddress());
        cn.setBuyerStateCode(inv.getBuyerStateCode());
        cn.setPlaceOfSupply(inv.getPlaceOfSupply());
        cn.setIsInterState(inv.getIsInterState());
        cn.setUtgstApplied(Boolean.TRUE.equals(inv.getUtgstApplied()));
        cn.setInvoiceType(inv.getInvoiceType() != null ? inv.getInvoiceType() : "B2C");

        cn.setTaxableAmount(round(inv.getTaxableAmount() * factor));
        cn.setCgstRate(inv.getCgstRate());
        cn.setSgstRate(inv.getSgstRate());
        cn.setIgstRate(inv.getIgstRate());
        cn.setCgstAmount(round(inv.getCgstAmount() * factor));
        cn.setSgstAmount(round(inv.getSgstAmount() * factor));
        cn.setIgstAmount(round(inv.getIgstAmount() * factor));
        cn.setTotalTax(round(cn.getCgstAmount() + cn.getSgstAmount() + cn.getIgstAmount()));
        cn.setTcsAmount(round(inv.getTcsAmount() * factor));
        cn.setTotalAmount(round(cn.getTaxableAmount() + cn.getTotalTax() + cn.getTcsAmount()));
        cn.setItems(buildItemsFromInvoice(cn, inv, factor));

        cn.setCreditNoteNumber(generateCreditNoteNumber("CN"));
        cn.setStatus("GENERATED");
        CreditNote saved = creditNoteRepo.save(cn);
        writeTcsReversal(saved, saved.getOrderId());

        auditService.log(userId, userId != null ? "seller:" + userId : "system", "CREDIT_NOTE_GENERATED",
                "CreditNote", saved.getId(),
                "Credit note " + saved.getCreditNoteNumber() + " against invoice " + inv.getInvoiceNumber(), null);

        return Map.of("creditNote", saved, "message", "Credit note generated successfully");
    }

    public Map<String, Object> getCreditNoteById(Long id, Long userId, String userRole) {
        CreditNote cn = creditNoteRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Credit note not found"));
        checkCreditNoteOwnership(cn, userId, userRole);
        cn.setItems(creditNoteItemRepo.findByCreditNoteId(id));
        return Map.of("creditNote", cn);
    }

    public Map<String, Object> listCreditNotes(Long userId, String userRole, int page, int size) {
        Page<CreditNote> result;
        if ("ADMIN".equals(userRole) || "SUPER_ADMIN".equals(userRole)) {
            result = creditNoteRepo.findAll(PageRequest.of(page, size));
        } else if ("SELLER".equals(userRole)) {
            result = creditNoteRepo.findBySellerIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        } else {
            result = creditNoteRepo.findByCustomerIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        }
        for (CreditNote cn : result.getContent()) {
            cn.setItems(creditNoteItemRepo.findByCreditNoteId(cn.getId()));
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("content", result.getContent());
        resp.put("totalPages", result.getTotalPages());
        resp.put("totalElements", result.getTotalElements());
        resp.put("page", page);
        resp.put("size", size);
        return resp;
    }

    private void checkCreditNoteOwnership(CreditNote cn, Long userId, String userRole) {
        if (userRole == null) userRole = "CUSTOMER";
        String role = userRole.toUpperCase();
        if ("ADMIN".equals(role) || "SUPER_ADMIN".equals(role)) return;
        if ("SELLER".equals(role) && userId.equals(cn.getSellerId())) return;
        if ("CUSTOMER".equals(role) && userId.equals(cn.getCustomerId())) return;
        throw new RuntimeException("Access denied: you do not have permission to view this credit note");
    }

    /** GSTR-3B Table 3.1(a) outward supplies (net of credit notes) + ITC summary for the period. */
    public Map<String, Object> getGstr3bData(Long sellerId, LocalDate startDate, LocalDate endDate) {
        String sellerGstin = configRepo.findBySellerId(sellerId).map(GstConfiguration::getGstin)
                .orElseGet(() -> sellerRegRepo.findByUserId(sellerId).map(SellerRegistration::getGstin).orElse(""));
        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        List<GstInvoice> invoices = invoiceRepo.findBySellerGstinAndInvoiceDateBetween(sellerGstin, start, end);
        List<CreditNote> creditNotes = creditNoteRepo.findBySellerGstinAndCreditNoteDateBetween(sellerGstin, start, end);

        double taxable = 0, cgst = 0, sgst = 0, igst = 0;
        double cnTaxable = 0, cnCgst = 0, cnSgst = 0, cnIgst = 0;
        double itcEligibleTaxable = 0, itcEligibleCgst = 0, itcEligibleSgst = 0, itcEligibleIgst = 0;
        int invoiceCount = 0, creditNoteCount = 0, b2bCount = 0, b2cCount = 0;

        for (GstInvoice inv : invoices) {
            taxable += nz(inv.getTaxableAmount());
            cgst += nz(inv.getCgstAmount());
            sgst += nz(inv.getSgstAmount());
            igst += nz(inv.getIgstAmount());
            invoiceCount++;
            if (Boolean.TRUE.equals(inv.getItcEligible())) {
                itcEligibleTaxable += nz(inv.getTaxableAmount());
                itcEligibleCgst += nz(inv.getCgstAmount());
                itcEligibleSgst += nz(inv.getSgstAmount());
                itcEligibleIgst += nz(inv.getIgstAmount());
            }
            if ("B2B".equals(inv.getInvoiceType())) b2bCount++; else b2cCount++;
        }
        for (CreditNote cn : creditNotes) {
            cnTaxable += nz(cn.getTaxableAmount());
            cnCgst += nz(cn.getCgstAmount());
            cnSgst += nz(cn.getSgstAmount());
            cnIgst += nz(cn.getIgstAmount());
            creditNoteCount++;
        }

        Map<String, Object> table3 = new LinkedHashMap<>();
        table3.put("taxableValue", round(taxable - cnTaxable));
        table3.put("cgst", round(cgst - cnCgst));
        table3.put("sgst", round(sgst - cnSgst));
        table3.put("igst", round(igst - cnIgst));
        table3.put("totalTax", round((cgst - cnCgst) + (sgst - cnSgst) + (igst - cnIgst)));

        Map<String, Object> itc = new LinkedHashMap<>();
        itc.put("eligibleTaxableValue", round(itcEligibleTaxable));
        itc.put("eligibleCgst", round(itcEligibleCgst));
        itc.put("eligibleSgst", round(itcEligibleSgst));
        itc.put("eligibleIgst", round(itcEligibleIgst));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("gstin", sellerGstin);
        result.put("periodStart", start.toString());
        result.put("periodEnd", end.toString());
        result.put("table3_1_outwardSupplies", table3);
        result.put("table3_1b_itc", itc);
        result.put("invoiceCount", invoiceCount);
        result.put("creditNoteCount", creditNoteCount);
        result.put("b2bCount", b2bCount);
        result.put("b2cCount", b2cCount);
        result.put("grossTaxableValue", round(taxable));
        result.put("grossCreditNotesValue", round(cnTaxable));
        return result;
    }

    public List<Map<String, Object>> getGstr3bRows(Long sellerId, LocalDate startDate, LocalDate endDate) {
        Map<String, Object> summary = getGstr3bData(sellerId, startDate, endDate);
        @SuppressWarnings("unchecked")
        Map<String, Object> t3 = (Map<String, Object>) summary.get("table3_1_outwardSupplies");
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("section", "3.1(a) Outward taxable supplies");
        row.put("taxableValue", t3.get("taxableValue"));
        row.put("cgst", t3.get("cgst"));
        row.put("sgst", t3.get("sgst"));
        row.put("igst", t3.get("igst"));
        rows.add(row);
        return rows;
    }

    public byte[] generateCreditNotePdf(Long id) throws DocumentException {
        CreditNote cn = creditNoteRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Credit note not found: " + id));
        cn.setItems(creditNoteItemRepo.findByCreditNoteId(id));

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

        boolean interState = Boolean.TRUE.equals(cn.getIsInterState());
        boolean utgst = Boolean.TRUE.equals(cn.getUtgstApplied());
        String stateLabel = utgst ? "UTGST" : "SGST";

        doc.add(PdfBrandingUtil.buildBrandHeader());
        doc.add(PdfBrandingUtil.buildDivider());
        doc.add(new Paragraph(" "));

        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{60, 40});
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.addElement(new Paragraph("Credit Note", bold14));
        leftCell.addElement(new Paragraph(
                "Original for Recipient" + (Boolean.TRUE.equals(cn.getIsInterState()) ? " (Inter-State)" : " (Intra-State)"),
                small8));
        if (cn.getReason() != null) {
            leftCell.addElement(new Paragraph("Reason: " + cn.getReason(), small8));
        }
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(new Paragraph("Credit Note No.", small8));
        rightCell.addElement(new Paragraph(safeStr(cn.getCreditNoteNumber()), bold12));
        rightCell.addElement(new Paragraph("Date", small8));
        rightCell.addElement(new Paragraph(cn.getCreditNoteDate() != null ? cn.getCreditNoteDate().toString() : "", normal9));
        if (cn.getOriginalInvoiceNumber() != null) {
            rightCell.addElement(new Paragraph("Against Invoice: " + cn.getOriginalInvoiceNumber(), small8));
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
        sellCell.addElement(new Paragraph(safeStr(cn.getSellerLegalName()), normal9));
        sellCell.addElement(new Paragraph("GSTIN: " + safeStr(cn.getSellerGstin()), normal9));
        sellCell.addElement(new Paragraph("Address: " + safeStr(cn.getSellerAddress()), small8));
        parties.addCell(sellCell);

        PdfPCell buyCell = new PdfPCell();
        buyCell.setPadding(8);
        buyCell.addElement(new Paragraph("Buyer / Recipient", bold10));
        buyCell.addElement(new Paragraph(safeStr(cn.getBuyerName()), normal9));
        buyCell.addElement(new Paragraph("GSTIN: " + (cn.getBuyerGstin() != null ? cn.getBuyerGstin() : "URP"), normal9));
        buyCell.addElement(new Paragraph("Address: " + safeStr(cn.getBuyerAddress()), small8));
        parties.addCell(buyCell);
        doc.add(parties);
        doc.add(new Paragraph(" "));

        int numColumns = Boolean.TRUE.equals(cn.getIsInterState()) ? 8 : 9;
        PdfPTable table = new PdfPTable(numColumns);
        table.setWidthPercentage(100);
        float[] colWidths = Boolean.TRUE.equals(cn.getIsInterState())
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
        for (CreditNoteItem item : cn.getItems()) {
            table.addCell(new Phrase(String.valueOf(sn++), normal9));
            table.addCell(new Phrase(safeStr(item.getHsnCode()), small8));
            table.addCell(new Phrase(safeStr(item.getProductName()), normal9));
            table.addCell(new Phrase(String.valueOf(item.getQuantity()), normal9));
            table.addCell(new Phrase("\u20B9" + String.format("%.2f", nz(item.getUnitPrice())), normal9));
            table.addCell(new Phrase("\u20B9" + String.format("%.2f", nz(item.getTaxableValue())), normal9));
            if (Boolean.TRUE.equals(cn.getIsInterState())) {
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
        addSumRow(sumTable, "Taxable Amount", "\u20B9" + String.format("%.2f", nz(cn.getTaxableAmount())), normal9);
        if (interState) {
            addSumRow(sumTable, "IGST", "\u20B9" + String.format("%.2f", nz(cn.getIgstAmount())), normal9);
        } else {
            addSumRow(sumTable, "CGST", "\u20B9" + String.format("%.2f", nz(cn.getCgstAmount())), normal9);
            addSumRow(sumTable, stateLabel, "\u20B9" + String.format("%.2f", nz(cn.getSgstAmount())), normal9);
        }
        addSumRow(sumTable, "Total Tax", "\u20B9" + String.format("%.2f", nz(cn.getTotalTax())), normal9);
        if (nz(cn.getTcsAmount()) > 0) {
            addSumRow(sumTable, "TCS", "\u20B9" + String.format("%.2f", nz(cn.getTcsAmount())), normal9);
        }
        PdfPCell totalCell = new PdfPCell(new Phrase("Total Amount Credited", bold12));
        totalCell.setBorder(Rectangle.TOP);
        sumTable.addCell(totalCell);
        PdfPCell totalVal = new PdfPCell(new Phrase("\u20B9" + String.format("%.2f", nz(cn.getTotalAmount())), bold12));
        totalVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalVal.setBorder(Rectangle.TOP);
        sumTable.addCell(totalVal);
        doc.add(sumTable);

        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Amount in Words: " + numberToWordsPdf(nz(cn.getTotalAmount())), bold9));
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Declaration: This credit note cancels the tax charged on the original supply and adjusts the return in GSTR-1 / GSTR-3B for the period in which it is issued.", small8));

        doc.close();
        return baos.toByteArray();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void applySellerDetails(CreditNote cn, Order order, GstInvoice inv) {
        Long sellerId = order.getSellerId();
        if (sellerId == null && inv != null) sellerId = inv.getSellerId();
        cn.setSellerId(sellerId);
        GstConfiguration config = sellerId != null ? configRepo.findBySellerId(sellerId).orElse(null) : null;
        String gstin = null;
        String legalName = null;
        String address = null;
        if (config != null) {
            gstin = config.getGstin();
            legalName = config.getLegalName();
            address = config.getAddress();
        } else if (sellerId != null) {
            Optional<SellerRegistration> reg = sellerRegRepo.findByUserId(sellerId);
            gstin = reg.map(SellerRegistration::getGstin).orElse(null);
            legalName = reg.map(SellerRegistration::getBusinessName).orElse(null);
            address = reg.map(SellerRegistration::getBusinessAddress).orElse(null);
        }
        if (inv != null) {
            if (gstin == null) gstin = inv.getSellerGstin();
            if (legalName == null) legalName = inv.getSellerLegalName();
            if (address == null) address = inv.getSellerAddress();
        }
        cn.setSellerGstin(gstin != null ? gstin : "");
        cn.setSellerLegalName(legalName != null ? legalName : "Cauvery Store");
        cn.setSellerAddress(address != null ? address : "");
    }

    private void applyBuyerDetails(CreditNote cn, Order order, GstInvoice inv) {
        if (inv != null) {
            cn.setCustomerId(inv.getCustomerId());
            cn.setCustomerEmail(inv.getCustomerEmail());
            cn.setBuyerGstin(inv.getBuyerGstin() != null ? inv.getBuyerGstin() : "URP");
            cn.setBuyerName(inv.getBuyerName());
            cn.setBuyerAddress(inv.getBuyerAddress());
            cn.setBuyerStateCode(inv.getBuyerStateCode());
            cn.setPlaceOfSupply(inv.getPlaceOfSupply());
            cn.setIsInterState(inv.getIsInterState());
            cn.setUtgstApplied(Boolean.TRUE.equals(inv.getUtgstApplied()));
            cn.setInvoiceType(inv.getInvoiceType() != null ? inv.getInvoiceType() : "B2C");
            return;
        }
        User buyer = order.getUser();
        cn.setCustomerId(buyer != null ? buyer.getId() : null);
        cn.setCustomerEmail(buyer != null ? buyer.getEmail() : null);
        cn.setBuyerGstin("URP");
        cn.setBuyerName(buyer != null ? buyer.getFullName() : "Walk-in Customer");
        Address addr = order.getAddress();
        if (addr != null) {
            cn.setBuyerAddress(String.join(", ",
                    addr.getStreet() != null ? addr.getStreet() : "",
                    addr.getCity() != null ? addr.getCity() : "",
                    addr.getState() != null ? addr.getState() : "",
                    addr.getPincode() != null ? addr.getPincode() : ""
            ).replaceAll("^,\\s*|,\\s*$", "").replaceAll(",\\s*,", ","));
        } else {
            cn.setBuyerAddress("");
        }
        String buyerStateCode = order.getAddress() != null ? getStateCode(order.getAddress().getState()) : "33";
        String sellerStateCode = "33";
        cn.setBuyerStateCode(buyerStateCode);
        cn.setPlaceOfSupply(buyerStateCode + "-" + STATE_CODES.getOrDefault(buyerStateCode, "Other"));
        cn.setIsInterState(!buyerStateCode.equals(sellerStateCode));
        cn.setUtgstApplied(buyerStateCode.equals(sellerStateCode) && GstComplianceUtil.isUtgstState(sellerStateCode));
    }

    private List<CreditNoteItem> buildItemsFromInvoice(CreditNote cn, GstInvoice inv, double factor) {
        List<CreditNoteItem> list = new ArrayList<>();
        for (GstInvoiceItem s : invoiceItemRepo.findByInvoiceId(inv.getId())) {
            if (s.getQuantity() == null || s.getQuantity() == 0) continue;
            CreditNoteItem it = new CreditNoteItem();
            it.setCreditNote(cn);
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

    private List<CreditNoteItem> buildItemsForReturn(CreditNote cn, GstInvoice inv, ReturnRequest rr, int returnedQty) {
        List<CreditNoteItem> list = new ArrayList<>();
        String matchName = rr.getProduct() != null ? rr.getProduct().getName() : null;
        for (GstInvoiceItem s : invoiceItemRepo.findByInvoiceId(inv.getId())) {
            if (s.getQuantity() == null || s.getQuantity() == 0) continue;
            if (matchName != null && !matchName.equals(s.getProductName())) continue;
            double factor = Math.min(1.0, (double) returnedQty / s.getQuantity());
            CreditNoteItem it = new CreditNoteItem();
            it.setCreditNote(cn);
            it.setProductName(s.getProductName());
            it.setHsnCode(s.getHsnCode());
            it.setSacCode(s.getSacCode());
            it.setUnitOfMeasure(s.getUnitOfMeasure() != null ? s.getUnitOfMeasure() : "NOS");
            it.setQuantity(returnedQty);
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

    private List<CreditNoteItem> buildItemsFromOrder(CreditNote cn, Order order) {
        List<CreditNoteItem> list = new ArrayList<>();
        boolean isInter = Boolean.TRUE.equals(cn.getIsInterState());
        for (OrderItem oi : order.getItems()) {
            Product p = oi.getProduct();
            double gstPct = (p != null && p.getGstPercentage() != null) ? p.getGstPercentage() : 12.0;
            double taxable = oi.getPrice() * oi.getQuantity();
            CreditNoteItem it = new CreditNoteItem();
            it.setCreditNote(cn);
            it.setProductName(p != null ? p.getName() : "Product");
            it.setHsnCode(p != null && p.getHsnCode() != null ? p.getHsnCode() : "999999");
            it.setUnitOfMeasure("NOS");
            it.setQuantity(oi.getQuantity());
            it.setUnitPrice(oi.getPrice());
            it.setTaxableValue(round(taxable));
            if (isInter) {
                it.setIgstRate(gstPct);
                it.setIgstAmount(round(taxable * gstPct / 100));
                it.setCgstRate(0.0); it.setCgstAmount(0.0);
                it.setSgstRate(0.0); it.setSgstAmount(0.0);
            } else {
                it.setCgstRate(gstPct / 2);
                it.setCgstAmount(round(taxable * gstPct / 200));
                it.setSgstRate(gstPct / 2);
                it.setSgstAmount(round(taxable * gstPct / 200));
                it.setIgstRate(0.0); it.setIgstAmount(0.0);
            }
            it.setTotalAmount(round(nz(it.getTaxableValue()) + nz(it.getCgstAmount())
                    + nz(it.getSgstAmount()) + nz(it.getIgstAmount())));
            list.add(it);
        }
        return list;
    }

    private List<CreditNoteItem> buildItemsFromOrderItem(CreditNote cn, OrderItem oi, int returnedQty, Order order) {
        List<CreditNoteItem> list = new ArrayList<>();
        Product p = oi.getProduct();
        double gstPct = (p != null && p.getGstPercentage() != null) ? p.getGstPercentage() : 12.0;
        double taxable = oi.getPrice() * returnedQty;
        boolean isInter = Boolean.TRUE.equals(cn.getIsInterState());
        CreditNoteItem it = new CreditNoteItem();
        it.setCreditNote(cn);
        it.setProductName(p != null ? p.getName() : "Product");
        it.setHsnCode(p != null && p.getHsnCode() != null ? p.getHsnCode() : "999999");
        it.setUnitOfMeasure("NOS");
        it.setQuantity(returnedQty);
        it.setUnitPrice(oi.getPrice());
        it.setTaxableValue(round(taxable));
        if (isInter) {
            it.setIgstRate(gstPct);
            it.setIgstAmount(round(taxable * gstPct / 100));
            it.setCgstRate(0.0); it.setCgstAmount(0.0);
            it.setSgstRate(0.0); it.setSgstAmount(0.0);
        } else {
            it.setCgstRate(gstPct / 2);
            it.setCgstAmount(round(taxable * gstPct / 200));
            it.setSgstRate(gstPct / 2);
            it.setSgstAmount(round(taxable * gstPct / 200));
            it.setIgstRate(0.0); it.setIgstAmount(0.0);
        }
        it.setTotalAmount(round(nz(it.getTaxableValue()) + nz(it.getCgstAmount())
                + nz(it.getSgstAmount()) + nz(it.getIgstAmount())));
        list.add(it);
        return list;
    }

    private String generateCreditNoteNumber(String prefix) {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM"));
        String safePrefix = prefix != null ? prefix.trim().toUpperCase().replaceAll("[^A-Z0-9]", "") : "CN";
        if (safePrefix.isEmpty()) safePrefix = "CN";
        if (safePrefix.length() > 9) safePrefix = safePrefix.substring(0, 9);
        String prefixPattern = safePrefix + datePart + "/";
        String maxNum = creditNoteRepo.findMaxCreditNoteNumberByPrefix(prefixPattern);
        int seq = 1;
        if (maxNum != null) {
            String[] parts = maxNum.split("/");
            seq = Integer.parseInt(parts[parts.length - 1]) + 1;
        }
        return prefixPattern + String.format("%05d", seq);
    }

    private String getStateCode(String stateName) {
        if (stateName == null) return "33";
        for (Map.Entry<String, String> e : STATE_CODES.entrySet()) {
            if (e.getValue().equalsIgnoreCase(stateName.trim())) return e.getKey();
        }
        return "33";
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private double nz(Double v) {
        return v != null ? v : 0.0;
    }

    private double nz(Double v, double fallback) {
        return v != null ? v : fallback;
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
