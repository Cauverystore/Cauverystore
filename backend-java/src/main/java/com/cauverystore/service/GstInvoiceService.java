package com.cauverystore.service;

import com.cauverystore.client.GstnClient;
import com.cauverystore.entities.GstInvoice;
import com.cauverystore.entities.GstInvoiceItem;
import com.cauverystore.entities.GstSyncQueue;
import com.cauverystore.entities.PincodeStateRange;
import com.cauverystore.repository.PincodeStateRangeRepository;
import com.cauverystore.entities.StateMaster;
import com.cauverystore.repository.StateMasterRepository;
import com.cauverystore.entities.GstConfiguration;
import com.cauverystore.entities.SellerRegistration;
import com.cauverystore.entities.TcsRecord;
import com.cauverystore.entities.Order;
import com.cauverystore.entities.OrderItem;
import com.cauverystore.entities.Product;
import com.cauverystore.entities.Address;
import com.cauverystore.entities.User;
import com.cauverystore.repository.GstInvoiceRepository;
import com.cauverystore.repository.GstInvoiceItemRepository;
import com.cauverystore.repository.GstSyncQueueRepository;
import com.cauverystore.repository.GstConfigurationRepository;
import com.cauverystore.repository.SellerRegistrationRepository;
import com.cauverystore.repository.OrderRepository;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.repository.UserRepository;
import com.cauverystore.repository.TcsRecordRepository;
import com.cauverystore.util.GstComplianceUtil;
import com.cauverystore.util.PdfBrandingUtil;
import com.cauverystore.util.ExcelExportUtil;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GstInvoiceService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(GstInvoiceService.class);

    private final GstInvoiceRepository invoiceRepo;
    private final GstInvoiceItemRepository itemRepo;
    private final GstSyncQueueRepository syncRepo;
    private final GstConfigurationRepository configRepo;
    private final SellerRegistrationRepository sellerRegRepo;
    private final OrderRepository orderRepo;
    private final ProductRepository productRepo;
    private final UserRepository userRepo;
    private final AuditService auditService;
    private final GstnClient gstnClient;
    private final TcsRecordRepository tcsRepo;
    private final StateMasterRepository stateRepo;
    private final PincodeStateRangeRepository pincodeRepo;


    private final GstRateResolver gstRateResolver;
    private final Gstr1ExportService gstr1ExportService;

    public GstInvoiceService(GstInvoiceRepository invoiceRepo, GstInvoiceItemRepository itemRepo,
                             GstSyncQueueRepository syncRepo, GstConfigurationRepository configRepo,
                             SellerRegistrationRepository sellerRegRepo,
                             OrderRepository orderRepo, ProductRepository productRepo,
                             UserRepository userRepo, AuditService auditService,
                             GstnClient gstnClient, TcsRecordRepository tcsRepo,
                             StateMasterRepository stateRepo,
                             PincodeStateRangeRepository pincodeRepo,
                             GstRateResolver gstRateResolver,
                             Gstr1ExportService gstr1ExportService) {
        this.gstRateResolver = gstRateResolver;
        this.gstr1ExportService = gstr1ExportService;
        this.invoiceRepo = invoiceRepo;
        this.itemRepo = itemRepo;
        this.syncRepo = syncRepo;
        this.configRepo = configRepo;
        this.sellerRegRepo = sellerRegRepo;
        this.orderRepo = orderRepo;
        this.productRepo = productRepo;
        this.userRepo = userRepo;
        this.auditService = auditService;
        this.gstnClient = gstnClient;
        this.tcsRepo = tcsRepo;
        this.stateRepo = stateRepo;
        this.pincodeRepo = pincodeRepo;
    }

    @Transactional
    public Map<String, Object> generateInvoiceFromOrder(Long orderId, Long userId, String gstin) {
        return generateInvoiceFromOrder(orderId, userId, gstin, null, null);
    }

    @Transactional
    public Map<String, Object> generateInvoiceFromOrder(Long orderId, Long userId, String gstin, String buyerGstin) {
        return generateInvoiceFromOrder(orderId, userId, gstin, buyerGstin, null);
    }

    @Transactional
    public Map<String, Object> generateInvoiceFromOrder(Long orderId, Long userId, String gstin, String buyerGstin, String deliveryStateCode) {
        GstComplianceUtil.validateGstin(gstin);
        if (buyerGstin != null && !buyerGstin.isBlank() && !"URP".equalsIgnoreCase(buyerGstin.trim())) {
            GstComplianceUtil.validateGstin(buyerGstin);
        }

        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        Optional<GstInvoice> existing = invoiceRepo.findByOrderId(orderId);
        if (existing.isPresent()) {
            return Map.of("invoice", existing.get(), "message", "Invoice already exists for this order");
        }

        GstConfiguration config = configRepo.findByGstin(gstin).orElse(null);
        Optional<SellerRegistration> sellerReg = sellerRegRepo.findByUserId(userId);

        String sellerLegalName = config != null ? config.getLegalName()
                : sellerReg.map(SellerRegistration::getBusinessName).orElse("Seller");
        String sellerAddress = config != null ? config.getAddress()
                : sellerReg.map(SellerRegistration::getBusinessAddress).orElse("");

        User buyer = order.getUser();

        GstInvoice inv = new GstInvoice();
        inv.setOrderId(orderId);
        inv.setSellerId(userId);
        inv.setCustomerId(buyer != null ? buyer.getId() : null);
        inv.setCustomerEmail(buyer != null ? buyer.getEmail() : null);
        inv.setSellerGstin(gstin);
        inv.setSellerLegalName(sellerLegalName);
        inv.setSellerAddress(sellerAddress);
        inv.setInvoiceDate(LocalDate.now());
        assertBooksAreOpen(inv.getSellerId(), inv.getInvoiceDate());

        boolean isB2b = buyerGstin != null && !buyerGstin.isBlank() && !"URP".equalsIgnoreCase(buyerGstin.trim());
        inv.setBuyerGstin(isB2b ? buyerGstin.toUpperCase() : "URP");
        inv.setInvoiceType(isB2b ? "B2B" : "B2C");
        inv.setItcEligible(isB2b);
        // B2B invoices carry the recipient's registered legal name (captured at checkout / manual
        // generation); B2C invoices use the account holder's name as the consumer.
        String buyerLegalName = order.getBuyerLegalName();
        inv.setBuyerName(isB2b && buyerLegalName != null && !buyerLegalName.isBlank()
                ? buyerLegalName.trim()
                : (buyer != null ? buyer.getFullName() : "Walk-in Customer"));
        // Payment terms, snapshotted rather than read back from the order. A COD order settled
        // by card later would otherwise change what an already-issued invoice says it was.
        inv.setPaymentMode(order.getPaymentMethod());
        // Only a supply on credit has anything outstanding. Cash on delivery falls due when the
        // goods arrive; anything prepaid has no due date, and printing one invites a second
        // payment against an invoice that is already settled.
        inv.setPaymentDueDate(inv.isPayableOnDelivery() ? inv.getInvoiceDate() : null);

        Address addr = order.getAddress();

        // Billing address (Req 3) is taken from the order's saved address. In this marketplace the
        // checkout collects a single address used for both billing and delivery, so the delivery
        // address (Req 4) defaults to the same value; callers may pass an explicit delivery state
        // code when the place of supply differs from the billing state.
        String buyerAddress = formatAddress(addr);
        inv.setBuyerAddress(buyerAddress);
        inv.setDeliveryAddress(buyerAddress);

        String sellerStateCode = config != null && config.getStateCode() != null ? config.getStateCode() : "33";
        // For B2B the recipient's registered state is the GSTIN state code (authoritative), not the
        // address field; for B2C it derives from the shipping address state.
        String buyerStateCode = isB2b
                ? inv.getBuyerGstin().substring(0, 2)
                : resolveDeliveryState(addr);
        String resolvedDeliveryStateCode = deliveryStateCode != null && !deliveryStateCode.isBlank()
                ? deliveryStateCode.trim()
                : buyerStateCode;
        inv.setBuyerStateCode(buyerStateCode);
        inv.setDeliveryStateCode(resolvedDeliveryStateCode);
        // Place of supply (Req 5) is the destination (delivery) state.
        inv.setPlaceOfSupply(resolvedDeliveryStateCode + "-" + stateName(resolvedDeliveryStateCode));
        inv.setIsInterState(!resolvedDeliveryStateCode.equals(sellerStateCode));
        // The state component of an intra-state supply from a Union Territory without a
        // legislature is UTGST, not SGST. The amount stays in the state-tax fields (GSTN reports
        // both under "State/UT Tax"); the flag only changes the label on the invoice.
        inv.setUtgstApplied(GstComplianceUtil.isUtgstState(sellerStateCode)
                && !Boolean.TRUE.equals(inv.getIsInterState()));

        String invoiceNumber = generateInvoiceNumber(config != null ? config.getInvoicePrefix() : "CS");
        GstComplianceUtil.validateInvoiceNumber(invoiceNumber);
        inv.setInvoiceNumber(invoiceNumber);
        inv.setStatus("PENDING_DISPATCH");
        inv.setInvoiceStatus("PENDING");

        double taxableAmount = 0;
        double totalCgst = 0, totalSgst = 0, totalIgst = 0;
        List<GstInvoiceItem> items = new ArrayList<>();

        // Product-level discounts are already baked into OrderItem.price. Coupon discounts are
        // order-level amounts: allocate them across the goods lines so GST applies on the net
        // taxable value after the pre-tax discount (Req 8).
        double rawSubtotal = 0;
        for (OrderItem oi : order.getItems()) {
            rawSubtotal += oi.getPrice() * oi.getQuantity();
        }
        double deliveryCharge = order.getDeliveryCharge() != null ? order.getDeliveryCharge() : 0;
        double orderTax = order.getTax() != null ? order.getTax() : 0;
        double couponDiscount = 0;
        if (rawSubtotal > 0 && order.getTotalAmount() > 0) {
            couponDiscount = rawSubtotal - order.getTotalAmount() + orderTax + deliveryCharge;
            if (couponDiscount < 0) couponDiscount = 0;
            if (couponDiscount > rawSubtotal) couponDiscount = rawSubtotal;
        }
        double retainedFactor = rawSubtotal > 0 ? (rawSubtotal - couponDiscount) / rawSubtotal : 0;
        inv.setDiscountAmount(Math.round(couponDiscount * 100.0) / 100.0);
        inv.setDeliveryCharge(Math.round(deliveryCharge * 100.0) / 100.0);

        double goodsCgstRate = 0.0, goodsSgstRate = 0.0, goodsIgstRate = 0.0;
        boolean goodsTaxSet = false;

        for (OrderItem oi : order.getItems()) {
            Product p = oi.getProduct();
            // Rate comes from the CBIC rate master keyed on the product's HSN, resolved as of
            // the order date, so the invoice declares exactly what checkout charged. It used to
            // read a free-text per-product percentage defaulting to 12%, which could differ from
            // the flat 12% checkout applied - meaning an invoice could declare tax that was
            // never collected.
            LocalDate supplyDate = order.getCreatedAt() != null
                    ? order.getCreatedAt().toLocalDate() : LocalDate.now();
            GstRateResolver.Resolved resolved = gstRateResolver
                    .resolve(p, Boolean.TRUE.equals(inv.getIsInterState()), supplyDate, oi.getPrice());
            double gstPct = resolved.getTotalRate();
            // One unresolvable line taints the whole invoice, so the flag is set per line AND
            // raised to the header - the correction has to name the line, but anyone hunting
            // for bad invoices searches the header.
            if (!resolved.isFromMaster()) {
                inv.setTaxComplianceStatus(GstInvoice.COMPLIANCE_FALLBACK);
            }
            double itemValue = oi.getPrice() * oi.getQuantity();
            double taxable = itemValue * retainedFactor;
            taxable = Math.round(taxable * 100.0) / 100.0;
            taxableAmount += taxable;

            GstInvoiceItem item = new GstInvoiceItem();
            item.setInvoice(inv);
            item.setProductName(p != null ? p.getName() : "Product");
            // Mandatory HSN/SAC classification for every line item (Rule 46 & HSN Annexure, CGST
            // Rules): refuse to issue the invoice when the product lacks a classification code
            // instead of silently defaulting to a generic code.
            String hsn = p != null ? p.getHsnCode() : null;
            if (hsn == null || hsn.isBlank()) {
                throw new IllegalArgumentException("HSN/SAC code is mandatory for line item '"
                        + (p != null ? p.getName() : "Product")
                        + "'. Set the HSN/SAC on the product in the catalogue before generating the invoice.");
            }
            item.setHsnCode(hsn.trim());
            item.setQuantity(oi.getQuantity());
            item.setUnitPrice(oi.getPrice());
            item.setTaxableValue(taxable);
            item.setRateFromMaster(resolved.isFromMaster());
            item.setUnitOfMeasure("NOS");

            if (Boolean.TRUE.equals(inv.getIsInterState())) {
                item.setIgstRate(gstPct);
                item.setIgstAmount(Math.round(taxable * gstPct / 100 * 100.0) / 100.0);
                totalIgst += item.getIgstAmount();
                item.setCgstRate(0.0); item.setCgstAmount(0.0);
                item.setSgstRate(0.0); item.setSgstAmount(0.0);
            } else {
                item.setCgstRate(gstPct / 2);
                item.setCgstAmount(Math.round(taxable * gstPct / 200 * 100.0) / 100.0);
                item.setSgstRate(gstPct / 2);
                item.setSgstAmount(Math.round(taxable * gstPct / 200 * 100.0) / 100.0);
                totalCgst += item.getCgstAmount();
                totalSgst += item.getSgstAmount();
                item.setIgstRate(0.0); item.setIgstAmount(0.0);
            }

            if (!goodsTaxSet) {
                goodsCgstRate = item.getCgstRate() != null ? item.getCgstRate() : 0.0;
                goodsSgstRate = item.getSgstRate() != null ? item.getSgstRate() : 0.0;
                goodsIgstRate = item.getIgstRate() != null ? item.getIgstRate() : 0.0;
                goodsTaxSet = true;
            }

            item.setTotalAmount(taxable + (item.getIgstAmount() != null ? item.getIgstAmount() : 0)
                    + (item.getCgstAmount() != null ? item.getCgstAmount() : 0)
                    + (item.getSgstAmount() != null ? item.getSgstAmount() : 0));
            items.add(item);
        }

        // Delivery charge is a supply of services (courier/transport, SAC 996511) and is raised as
        // a separate line item so the HSN/SAC requirement (Req 7) and the tax split are correct for
        // goods vs services, instead of being folded into goods lines at the goods rate.
        if (deliveryCharge > 0) {
            double deliveryTaxable = Math.round(deliveryCharge * 100.0) / 100.0;
            taxableAmount += deliveryTaxable;
            GstInvoiceItem service = new GstInvoiceItem();
            service.setInvoice(inv);
            service.setProductName("Delivery Charges (Courier/Transport)");
            service.setSacCode("996511");
            service.setQuantity(1);
            service.setUnitPrice(deliveryCharge);
            service.setTaxableValue(deliveryTaxable);
            service.setUnitOfMeasure("NOS");
            if (Boolean.TRUE.equals(inv.getIsInterState())) {
                service.setIgstRate(18.0);
                service.setIgstAmount(Math.round(deliveryTaxable * 18.0 / 100 * 100.0) / 100.0);
                totalIgst += service.getIgstAmount();
                service.setCgstRate(0.0); service.setCgstAmount(0.0);
                service.setSgstRate(0.0); service.setSgstAmount(0.0);
            } else {
                service.setCgstRate(9.0);
                service.setCgstAmount(Math.round(deliveryTaxable * 9.0 / 100 * 100.0) / 100.0);
                service.setSgstRate(9.0);
                service.setSgstAmount(Math.round(deliveryTaxable * 9.0 / 100 * 100.0) / 100.0);
                totalCgst += service.getCgstAmount();
                totalSgst += service.getSgstAmount();
                service.setIgstRate(0.0); service.setIgstAmount(0.0);
            }
            service.setTotalAmount(deliveryTaxable + service.getIgstAmount() + service.getCgstAmount() + service.getSgstAmount());
            items.add(service);
        }

        inv.setItems(items);
        inv.setTaxableAmount(Math.round(taxableAmount * 100.0) / 100.0);
        inv.setB2cLarge(GstComplianceUtil.isB2cLarge(inv.getIsInterState(), inv.getTaxableAmount()));
        inv.setTotalTax(Math.round((totalCgst + totalSgst + totalIgst) * 100.0) / 100.0);
        inv.setCgstAmount(Math.round(totalCgst * 100.0) / 100.0);
        inv.setSgstAmount(Math.round(totalSgst * 100.0) / 100.0);
        inv.setIgstAmount(Math.round(totalIgst * 100.0) / 100.0);
        inv.setCgstRate(goodsCgstRate);
        inv.setSgstRate(Boolean.TRUE.equals(inv.getIsInterState()) ? 0.0 : goodsSgstRate);
        inv.setIgstRate(Boolean.TRUE.equals(inv.getIsInterState()) ? goodsIgstRate : 0.0);

        Double annualTurnover = config != null ? config.getAnnualTurnover() : null;
        inv.setHsnDigits(GstComplianceUtil.determineHsnDigits(annualTurnover));
        inv.setReverseCharge(false);
        inv.setSupplyType(deliveryCharge > 0 ? "GOODS AND SERVICES" : "GOODS");
        inv.setInvoiceCopyType("ORIGINAL");

        boolean einvoicingApplicable = GstComplianceUtil.requiresEInvoice(annualTurnover);
        inv.setEinvoicingRequired(einvoicingApplicable);

        // Generate IRN and QR code if e-invoicing applies (turnover >= 5Cr)
        if (einvoicingApplicable) {
            try {
                Map<String, Object> irnResp = gstnClient.generateIrn(inv);
                inv.setIrn((String) irnResp.get("irn"));
                inv.setQrCode((String) irnResp.get("qrCode"));
                inv.setAckNo((String) irnResp.get("ackNo"));
                inv.setAckDate((String) irnResp.get("ackDate"));
            } catch (RuntimeException e) {
                // The portal being down must not stop the sale or lose the invoice - but the
                // invoice must not pretend to be registered either. It is stored without an
                // IRN and queued, so the gap is visible and the scheduler can retry. A
                // fabricated IRN would look registered to everyone downstream, and a buyer
                // could claim input credit against a number the portal never issued.
                log.error("IRN registration failed for invoice {} - storing it unregistered and "
                        + "queueing for retry: {}", inv.getInvoiceNumber(), e.getMessage());
                inv.setStatus("PENDING_IRN");
            }
        }

        // Generate e-way bill when the consignment value exceeds ₹50,000 (Rule 138).
        if (GstComplianceUtil.requiresEWayBill(taxableAmount)) {
            Map<String, Object> ewbResp = gstnClient.generateEwayBill(inv);
            inv.setEwayBillNumber((String) ewbResp.get("ewayBillNumber"));
            Object validUpto = ewbResp.get("validUpto");
            if (validUpto != null) {
                inv.setEwayBillExpiry(LocalDate.parse(validUpto.toString()));
            }
        }

        double tcsRate = config != null ? config.getTcsRate() : 1.0;
        double tcsAmount = Math.round(taxableAmount * tcsRate / 100 * 100.0) / 100.0;
        inv.setTcsAmount(tcsAmount);
        inv.setTcsRate(tcsRate);

        // TCS is not the customer's to pay, and adding it here was charging them for it.
        //
        // Under section 52 the marketplace collects the consideration, keeps back 1% of the net
        // taxable supplies, and pays the seller the balance. It comes out of what the seller
        // receives - it is never an addition to the buyer's bill. Adding it made an order of
        // Rs 15,000 + Rs 2,700 GST come to Rs 17,850 instead of Rs 17,700, so every customer
        // was overcharged by 1% of the goods value and the invoice misdescribed what that
        // charge was.
        //
        // The amount is still recorded on the invoice, because it is what has to be paid over
        // and declared in GSTR-8. It just is not part of what the customer owes.
        inv.setTotalAmount(Math.round((taxableAmount + inv.getTotalTax()) * 100.0) / 100.0);

        // Supplier signature (Req 11): bind supplier GSTIN + fiscal identity + amounts with a digest
        // so the invoice cannot be altered without invalidating the recorded signature.
        inv.setSupplierSignature(sha256Digest(gstin + "|" + inv.getInvoiceNumber() + "|"
                + inv.getTaxableAmount() + "|" + inv.getTotalTax() + "|" + inv.getTotalAmount()));
        inv.setSignedBy(sellerLegalName);
        inv.setSignatureDate(inv.getInvoiceDate());

        // Rule 46 wants the supplier's actual signature on a tax invoice; the digest above is
        // tamper-evidence, not that. Copied onto the invoice at the moment it is raised rather
        // than looked up when it is printed, so a seller changing or removing their signature
        // later cannot silently alter every invoice they have already issued.
        sellerRegRepo.findByUserId(inv.getSellerId()).ifPresent(reg -> {
            inv.setSellerBusinessType(reg.getBusinessType());
            inv.setSellerBusinessCategory(reg.getBusinessCategory());
            inv.setSupplierSignatureImageUrl(reg.getSignatureImageUrl());
            if (reg.getAuthorisedSignatory() != null && !reg.getAuthorisedSignatory().isBlank()) {
                inv.setSignedBy(reg.getAuthorisedSignatory());
            }
        });

        GstInvoice saved = invoiceRepo.save(inv);

        // Record TCS for GSTR-8 (1% TCS on taxable value under Section 52)
        if (tcsAmount > 0) {
            TcsRecord tcs = new TcsRecord();
            tcs.setSellerGstin(saved.getSellerGstin());
            tcs.setMarketplace("NOYYAL");
            tcs.setOrderId(orderId);
            tcs.setInvoiceNumber(saved.getInvoiceNumber());
            tcs.setSellerId(saved.getSellerId());
            tcs.setCustomerId(saved.getCustomerId());
            tcs.setCustomerEmail(saved.getCustomerEmail());
            tcs.setCustomerGstin(isB2b ? saved.getBuyerGstin() : "");
            tcs.setTransactionDate(LocalDate.now());
            tcs.setTaxableAmount(saved.getTaxableAmount());
            tcs.setTcsRate(saved.getTcsRate());
            tcs.setTcsAmount(tcsAmount);
            tcs.setFilingStatus("PENDING");
            tcs.setPeriod(LocalDate.now().format(DateTimeFormatter.ofPattern("MMyyyy")));
            tcsRepo.save(tcs);
        }

        addToSyncQueue(saved.getId(), "GENERATE_EINVOICE");

        auditService.log(userId, "seller:" + userId, "INVOICE_GENERATED", "GstInvoice", saved.getId(),
                "Invoice " + saved.getInvoiceNumber() + " generated for order " + orderId, null);

        return Map.of("invoice", saved, "message", "Invoice generated successfully");
    }

    @Transactional
    public Map<String, Object> bulkGenerateInvoices(List<Long> orderIds, Long userId, String gstin) {
        if (orderIds == null || orderIds.isEmpty()) {
            throw new RuntimeException("orderIds are required");
        }
        GstComplianceUtil.validateGstin(gstin);
        int generated = 0;
        List<Map<String, Object>> results = new ArrayList<>();
        for (Long orderId : orderIds) {
            try {
                Map<String, Object> r = generateInvoiceFromOrder(orderId, userId, gstin, null);
                GstInvoice inv = (GstInvoice) r.get("invoice");
                generated++;
                results.add(Map.of("orderId", orderId, "invoiceNumber", inv.getInvoiceNumber(), "message", r.get("message")));
            } catch (Exception e) {
                results.add(Map.of("orderId", orderId, "error", e.getMessage()));
            }
        }
        return Map.of("generated", generated, "total", orderIds.size(), "results", results);
    }

    public byte[] exportCsv(String[] headers, List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", headers)).append("\n");
        for (Map<String, Object> row : rows) {
            sb.append(Arrays.stream(headers)
                    .map(h -> {
                        Object v = row.get(h);
                        String s = v != null ? String.valueOf(v) : "";
                        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
                            s = "\"" + s.replace("\"", "\"\"") + "\"";
                        }
                        return s;
                    })
                    .collect(Collectors.joining(","))).append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] exportJson(Object data) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(data);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize JSON export", e);
        }
    }

    public Map<String, Object> getInvoiceById(Long id, Long userId, String userRole) {
        GstInvoice inv = invoiceRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        checkInvoiceOwnership(inv, userId, userRole);
        List<GstInvoiceItem> items = itemRepo.findByInvoiceId(id);
        inv.setItems(items);
        return Map.of("invoice", inv);
    }

    public Map<String, Object> getInvoiceByOrder(Long orderId, Long userId, String userRole) {
        GstInvoice inv = invoiceRepo.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Invoice not found for order: " + orderId));
        checkInvoiceOwnership(inv, userId, userRole);
        List<GstInvoiceItem> items = itemRepo.findByInvoiceId(inv.getId());
        inv.setItems(items);
        return Map.of("invoice", inv);
    }

    public Map<String, Object> listInvoices(Long userId, String userRole, int page, int size) {
        Page<GstInvoice> result;
        if ("ADMIN".equals(userRole) || "SUPER_ADMIN".equals(userRole)) {
            result = invoiceRepo.findAll(PageRequest.of(page, size));
        } else if ("SELLER".equals(userRole)) {
            result = invoiceRepo.findBySellerIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        } else {
            result = invoiceRepo.findByCustomerIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("content", result.getContent());
        resp.put("totalPages", result.getTotalPages());
        resp.put("totalElements", result.getTotalElements());
        resp.put("page", page);
        resp.put("size", size);
        return resp;
    }

    private void checkInvoiceOwnership(GstInvoice inv, Long userId, String userRole) {
        if (userRole == null) userRole = "CUSTOMER";
        String role = userRole.toUpperCase();
        if ("ADMIN".equals(role) || "SUPER_ADMIN".equals(role)) return;
        if ("SELLER".equals(role) && userId.equals(inv.getSellerId())) return;
        if ("CUSTOMER".equals(role)) {
            if (userId.equals(inv.getCustomerId())) return;
            if (inv.getCustomerEmail() != null) {
                User user = userRepo.findById(userId).orElse(null);
                if (user != null && inv.getCustomerEmail().equalsIgnoreCase(user.getEmail())) return;
            }
        }
        throw new RuntimeException("Access denied: you do not have permission to view this invoice");
    }

    public Map<String, Object> listAllInvoices(int page, int size) {
        Page<GstInvoice> result = invoiceRepo.findAll(PageRequest.of(page, size));
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("content", result.getContent());
        resp.put("totalPages", result.getTotalPages());
        resp.put("totalElements", result.getTotalElements());
        resp.put("page", page);
        return resp;
    }

    public List<GstInvoice> getPendingSyncInvoices() {
        return invoiceRepo.findByStatusIn(List.of("GENERATED", "SYNC_FAILED"));
    }

    @Transactional
    public Map<String, Object> markSyncedToGstn(Long invoiceId, String irn, String qrCode, String ackNo, String ackDate) {
        GstInvoice inv = invoiceRepo.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        inv.setIrn(irn);
        inv.setQrCode(qrCode);
        inv.setAckNo(ackNo);
        inv.setAckDate(ackDate);
        inv.setStatus("SYNCED");
        inv.setLastSyncAttempt(LocalDateTime.now());
        invoiceRepo.save(inv);

        List<GstSyncQueue> queueItems = syncRepo.findByInvoiceIdAndSyncType(invoiceId, "GENERATE_EINVOICE");
        for (GstSyncQueue q : queueItems) {
            q.setStatus("COMPLETED");
            q.setResponsePayload("IRN: " + irn);
            syncRepo.save(q);
        }

        auditService.log(null, "system", "INVOICE_SYNCED", "GstInvoice", invoiceId,
                "Invoice " + inv.getInvoiceNumber() + " synced to GSTN. IRN: " + irn, null);
        return Map.of("invoice", inv, "message", "Invoice synced to GSTN");
    }

    @Transactional
    public Map<String, Object> markSyncFailed(Long invoiceId, String error) {
        GstInvoice inv = invoiceRepo.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        inv.setStatus("SYNC_FAILED");
        inv.setSyncError(error);
        inv.setSyncAttempts(inv.getSyncAttempts() != null ? inv.getSyncAttempts() + 1 : 1);
        inv.setLastSyncAttempt(LocalDateTime.now());
        invoiceRepo.save(inv);
        return Map.of("invoice", inv, "message", "Sync failed recorded");
    }

    @Transactional
    public void addToSyncQueue(Long invoiceId, String syncType) {
        GstSyncQueue q = new GstSyncQueue();
        q.setInvoiceId(invoiceId);
        q.setSyncType(syncType);
        q.setStatus("PENDING");
        syncRepo.save(q);
    }

    @Transactional
    public int processSyncQueue(int batchSize) {
        List<GstSyncQueue> pending = syncRepo.findByStatusOrderByCreatedAtAsc("PENDING");
        int processed = 0;
        for (GstSyncQueue q : pending) {
            if (processed >= batchSize) break;
            try {
                q.setStatus("PROCESSING");
                q.setLastAttemptAt(LocalDateTime.now());
                syncRepo.save(q);

                GstInvoice inv = invoiceRepo.findById(q.getInvoiceId()).orElse(null);
                if (inv == null) {
                    q.setStatus("FAILED");
                    q.setErrorMessage("Invoice not found");
                    syncRepo.save(q);
                    continue;
                }

                q.setRequestPayload("Invoice: " + inv.getInvoiceNumber());
                q.setStatus("QUEUED_FOR_GSTN");
                q.setResponsePayload("Queued for GSTN e-invoice API submission");
                syncRepo.save(q);
                processed++;
            } catch (Exception e) {
                q.setStatus("FAILED");
                q.setErrorMessage(e.getMessage());
                q.setRetryCount(q.getRetryCount() != null ? q.getRetryCount() + 1 : 1);
                syncRepo.save(q);
            }
        }
        return processed;
    }

    public Map<String, Object> getGstSummary(Long sellerId, LocalDate startDate, LocalDate endDate) {
        String sellerGstin = configRepo.findBySellerId(sellerId).map(GstConfiguration::getGstin)
                .orElseGet(() -> sellerRegRepo.findByUserId(sellerId).map(SellerRegistration::getGstin).orElse(""));
        List<GstInvoice> invoices = invoiceRepo.findBySellerGstinAndInvoiceDateBetween(
                sellerGstin,
                startDate != null ? startDate : LocalDate.now().withDayOfMonth(1),
                endDate != null ? endDate : LocalDate.now());

        double totalTaxable = 0, totalCgst = 0, totalSgst = 0, totalIgst = 0, totalTcs = 0;
        int intraState = 0, interState = 0;

        for (GstInvoice inv : invoices) {
            totalTaxable += inv.getTaxableAmount() != null ? inv.getTaxableAmount() : 0;
            totalCgst += inv.getCgstAmount() != null ? inv.getCgstAmount() : 0;
            totalSgst += inv.getSgstAmount() != null ? inv.getSgstAmount() : 0;
            totalIgst += inv.getIgstAmount() != null ? inv.getIgstAmount() : 0;
            totalTcs += inv.getTcsAmount() != null ? inv.getTcsAmount() : 0;
            if (Boolean.TRUE.equals(inv.getIsInterState())) interState++; else intraState++;
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalInvoices", invoices.size());
        summary.put("totalTaxableAmount", Math.round(totalTaxable * 100.0) / 100.0);
        summary.put("totalCgst", Math.round(totalCgst * 100.0) / 100.0);
        summary.put("totalSgst", Math.round(totalSgst * 100.0) / 100.0);
        summary.put("totalIgst", Math.round(totalIgst * 100.0) / 100.0);
        summary.put("totalTax", Math.round((totalCgst + totalSgst + totalIgst) * 100.0) / 100.0);
        summary.put("totalTcs", Math.round(totalTcs * 100.0) / 100.0);
        summary.put("intraStateCount", intraState);
        summary.put("interStateCount", interState);
        summary.put("periodStart", (startDate != null ? startDate : LocalDate.now().withDayOfMonth(1)).toString());
        summary.put("periodEnd", (endDate != null ? endDate : LocalDate.now()).toString());
        return summary;
    }

    /**
     * GSTR-1 in the offline utility's own format, section by section.
     *
     * Distinct from getGstr1Data, which is a readable report keyed on our own field names.
     * That one is for looking at; this one is for filing, and the utility will only accept
     * its own column headings.
     */
    public Map<String, List<Map<String, Object>>> getGstr1ForFiling(Long sellerId,
                                                                   LocalDate startDate,
                                                                   LocalDate endDate) {
        return gstr1ExportService.allSections(invoicesForPeriod(sellerId, startDate, endDate));
    }

    /**
     * GSTR-1 rows narrowed to one trade type and/or category.
     *
     * Filters on what was recorded on each invoice, not on the seller's current profile, so a
     * report run today for last quarter returns the same rows it would have returned then.
     */
    public List<Map<String, Object>> getGstr1DataFiltered(Long sellerId, LocalDate startDate,
                                                          LocalDate endDate, String businessType,
                                                          String businessCategory) {
        List<GstInvoice> invoices = invoicesForPeriod(sellerId, startDate, endDate).stream()
                .filter(inv -> matches(inv.getSellerBusinessType(), businessType))
                .filter(inv -> matches(inv.getSellerBusinessCategory(), businessCategory))
                .toList();
        return toGstr1Rows(invoices);
    }

    /**
     * Taxable value and tax collected, grouped by the seller's trade type and category - which
     * is the question these fields were collected to answer.
     */
    public List<Map<String, Object>> getGstBusinessSegmentSummary(Long sellerId, LocalDate startDate,
                                                                  LocalDate endDate) {
        Map<String, Map<String, Object>> bySegment = new LinkedHashMap<>();
        for (GstInvoice inv : invoicesForPeriod(sellerId, startDate, endDate)) {
            String type = inv.getSellerBusinessType() != null ? inv.getSellerBusinessType() : "Not stated";
            String category = inv.getSellerBusinessCategory() != null
                    ? inv.getSellerBusinessCategory() : "Not stated";
            Map<String, Object> row = bySegment.computeIfAbsent(type + "|" + category, k -> {
                Map<String, Object> fresh = new LinkedHashMap<>();
                fresh.put("businessType", type);
                fresh.put("businessCategory", category);
                fresh.put("invoiceCount", 0);
                fresh.put("taxableAmount", 0.0);
                fresh.put("totalTax", 0.0);
                return fresh;
            });
            row.put("invoiceCount", (Integer) row.get("invoiceCount") + 1);
            row.put("taxableAmount", round2((Double) row.get("taxableAmount") + nz(inv.getTaxableAmount())));
            row.put("totalTax", round2((Double) row.get("totalTax") + nz(inv.getTotalTax())));
        }
        return new ArrayList<>(bySegment.values());
    }

    /** Blank filter means "no filter"; otherwise compare ignoring case and spacing. */
    private boolean matches(String recorded, String filter) {
        if (filter == null || filter.isBlank()) return true;
        return recorded != null && recorded.trim().equalsIgnoreCase(filter.trim());
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private List<GstInvoice> invoicesForPeriod(Long sellerId, LocalDate startDate, LocalDate endDate) {
        String sellerGstin = configRepo.findBySellerId(sellerId).map(GstConfiguration::getGstin)
                .orElseGet(() -> sellerRegRepo.findByUserId(sellerId).map(SellerRegistration::getGstin).orElse(""));
        return invoiceRepo.findBySellerGstinAndInvoiceDateBetween(
                sellerGstin,
                startDate != null ? startDate : LocalDate.now().withDayOfMonth(1),
                endDate != null ? endDate : LocalDate.now());
    }

    public List<Map<String, Object>> getGstr1Data(Long sellerId, LocalDate startDate, LocalDate endDate) {
        return toGstr1Rows(invoicesForPeriod(sellerId, startDate, endDate));
    }

    /** Turns invoices into the readable GSTR-1 report rows. Shared with the filtered view. */
    private List<Map<String, Object>> toGstr1Rows(List<GstInvoice> invoices) {
        List<Map<String, Object>> gstr1 = new ArrayList<>();
        for (GstInvoice inv : invoices) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("invoiceNumber", inv.getInvoiceNumber());
            entry.put("invoiceDate", inv.getInvoiceDate().toString());
            entry.put("buyerGstin", inv.getBuyerGstin());
            entry.put("buyerName", inv.getBuyerName());
            entry.put("taxableAmount", inv.getTaxableAmount());
            entry.put("cgst", inv.getCgstAmount());
            entry.put("sgst", inv.getSgstAmount());
            entry.put("igst", inv.getIgstAmount());
            entry.put("totalTax", inv.getTotalTax());
            entry.put("totalAmount", inv.getTotalAmount());
            entry.put("placeOfSupply", inv.getPlaceOfSupply());
            entry.put("isInterState", inv.getIsInterState());
            entry.put("invoiceType", inv.getInvoiceType());
            entry.put("itcEligible", inv.getItcEligible());
            // B2C interstate supplies > ₹1,00,000 are B2CL (GSTR-1 6A) and must carry buyer GSTIN + full address.
            boolean b2cInterstateAboveLimit = "B2C".equals(inv.getInvoiceType())
                    && Boolean.TRUE.equals(inv.getIsInterState())
                    && nz(inv.getTaxableAmount()) > GstComplianceUtil.getB2cLargeThreshold();
            entry.put("b2cInterstateAboveLimit", b2cInterstateAboveLimit);
            entry.put("requiresBuyerGstin", b2cInterstateAboveLimit);
            entry.put("b2cLarge", inv.getB2cLarge());
            entry.put("irn", inv.getIrn());
            entry.put("status", inv.getStatus());
            gstr1.add(entry);
        }
        return gstr1;
    }

    /**
     * GSTR-1 data segregated into the filing tables that actually matter:
     * 4A = B2B (recipient has a valid GSTIN, ITC-eligible),
     * 6A = B2CL (B2C large - inter-state consumer sales above ₹1,00,000),
     * 6B = B2CS (B2C small - remaining consumer supplies),
     * 6C = Nil-rated / exempt / non-GST supplies.
     */
    public Map<String, Object> getGstr1Segregated(Long sellerId, LocalDate startDate, LocalDate endDate) {
        String sellerGstin = configRepo.findBySellerId(sellerId).map(GstConfiguration::getGstin)
                .orElseGet(() -> sellerRegRepo.findByUserId(sellerId).map(SellerRegistration::getGstin).orElse(""));
        LocalDate from = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate to = endDate != null ? endDate : LocalDate.now();
        List<GstInvoice> invoices = invoiceRepo.findBySellerGstinAndInvoiceDateBetween(sellerGstin, from, to);

        List<Map<String, Object>> b2b = new ArrayList<>();
        List<Map<String, Object>> b2cl = new ArrayList<>();
        List<Map<String, Object>> b2cs = new ArrayList<>();
        List<Map<String, Object>> nilRated = new ArrayList<>();

        for (GstInvoice inv : invoices) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("invoiceNumber", inv.getInvoiceNumber());
            row.put("invoiceDate", inv.getInvoiceDate().toString());
            row.put("buyerGstin", inv.getBuyerGstin());
            row.put("buyerName", inv.getBuyerName());
            row.put("taxableAmount", inv.getTaxableAmount());
            row.put("cgst", inv.getCgstAmount());
            row.put("sgst", inv.getSgstAmount());
            row.put("igst", inv.getIgstAmount());
            row.put("totalTax", inv.getTotalTax());
            row.put("totalAmount", inv.getTotalAmount());
            row.put("placeOfSupply", inv.getPlaceOfSupply());
            row.put("isInterState", inv.getIsInterState());
            row.put("invoiceType", inv.getInvoiceType());
            row.put("b2cLarge", inv.getB2cLarge());
            row.put("irn", inv.getIrn());
            row.put("status", inv.getStatus());

            boolean isB2b = inv.getBuyerGstin() != null && !"URP".equalsIgnoreCase(inv.getBuyerGstin().trim());
            if (isB2b) {
                b2b.add(row);
            } else if (Boolean.TRUE.equals(inv.getIsInterState())
                    && nz(inv.getTaxableAmount()) > GstComplianceUtil.getB2cLargeThreshold()) {
                b2cl.add(row);
            } else if (GstComplianceUtil.isNilRated(inv.getTaxableAmount(),
                    inv.getCgstAmount(), inv.getSgstAmount(), inv.getIgstAmount())) {
                nilRated.add(row);
            } else {
                b2cs.add(row);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("periodStart", from.toString());
        result.put("periodEnd", to.toString());
        result.put("sellerGstin", sellerGstin);
        result.put("4A", gstr1Section("B2B (With GSTIN) - Table 4A", b2b));
        result.put("6A", gstr1Section("B2CL (B2C Large - inter-state > \u20B91L) - Table 6A", b2cl));
        result.put("6B", gstr1Section("B2CS (B2C Small) - Table 6B", b2cs));
        result.put("6C", gstr1Section("Nil-rated / Exempt / Non-GST - Table 6C", nilRated));
        return result;
    }

    private Map<String, Object> gstr1Section(String label, List<Map<String, Object>> rows) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("invoices", rows);
        double taxable = 0, tax = 0;
        for (Map<String, Object> r : rows) {
            taxable += nz((Double) r.get("taxableAmount"));
            tax += nz((Double) r.get("totalTax"));
        }
        m.put("count", rows.size());
        m.put("totalTaxableAmount", Math.round(taxable * 100.0) / 100.0);
        m.put("totalTax", Math.round(tax * 100.0) / 100.0);
        return m;
    }

    public Map<String, Object> getTcsSummary(Long sellerId, LocalDate startDate, LocalDate endDate) {
        Map<String, Object> summary = getGstSummary(sellerId, startDate, endDate);
        Map<String, Object> tcs = new LinkedHashMap<>();
        tcs.put("totalTcsCollected", summary.get("totalTcs"));
        tcs.put("tcsRate", "1%");
        tcs.put("totalInvoices", summary.get("totalInvoices"));
        tcs.put("totalTaxableAmount", summary.get("totalTaxableAmount"));
        tcs.put("periodStart", summary.get("periodStart"));
        tcs.put("periodEnd", summary.get("periodEnd"));
        tcs.put("gstr8Applicable", true);
        return tcs;
    }

    public Map<String, Object> getConfigurations() {
        List<GstConfiguration> configs = configRepo.findByIsActiveTrue();
        List<Map<String, Object>> list = new ArrayList<>();
        for (GstConfiguration c : configs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("gstin", c.getGstin());
            m.put("legalName", c.getLegalName());
            m.put("tradeName", c.getTradeName());
            m.put("stateCode", c.getStateCode());
            m.put("stateName", c.getStateName());
            m.put("isActive", c.getIsActive());
            m.put("tcsRate", c.getTcsRate());
            m.put("invoicePrefix", c.getInvoicePrefix());
            m.put("annualTurnover", c.getAnnualTurnover());
            m.put("address", c.getAddress());
            list.add(m);
        }
        return Map.of("configurations", list, "total", list.size());
    }

    @Transactional
    /**
     * Saves the marketplace's own registration.
     *
     * Validated on the way in rather than trusted. A GSTIN that passes the shape check but
     * fails its checksum is a typo, and a typo here is not discovered until a return is
     * rejected - by which time the deadline has usually passed.
     */
    public Map<String, Object> saveConfiguration(GstConfiguration config) {
        validateConfiguration(config);
        if (config.getTcsRate() == null) config.setTcsRate(1.0);
        if (config.getInvoicePrefix() == null) config.setInvoicePrefix("CS");
        config.setIsActive(true);
        GstConfiguration saved = configRepo.save(config);
        auditService.log(null, "admin", "GST_CONFIG_SAVED", "GstConfiguration", saved.getId(),
                "GSTIN " + saved.getGstin() + " configured", null);
        return Map.of("configuration", saved, "message", "GST configuration saved");
    }

    private void validateConfiguration(GstConfiguration config) {
        if (isBlank(config.getGstin())) {
            throw new IllegalArgumentException("The marketplace's GSTIN is required.");
        }
        GstComplianceUtil.validateGstin(config.getGstin());
        if (!GstComplianceUtil.hasValidCheckDigit(config.getGstin())) {
            throw new IllegalArgumentException("'" + config.getGstin() + "' has the right shape but "
                    + "fails its checksum, so it is not a real GSTIN. Check it against the "
                    + "registration certificate.");
        }
        config.setGstin(config.getGstin().trim().toUpperCase());

        if (!isBlank(config.getTcsGstin())) {
            GstComplianceUtil.validateGstin(config.getTcsGstin());
            if (!GstComplianceUtil.hasValidCheckDigit(config.getTcsGstin())) {
                throw new IllegalArgumentException("The TCS GSTIN '" + config.getTcsGstin()
                        + "' fails its checksum, so it is not a real registration.");
            }
            config.setTcsGstin(config.getTcsGstin().trim().toUpperCase());
            // Almost always means the regular GSTIN was pasted into the TCS field. Left alone
            // it would file every GSTR-8 under the wrong registration.
            if (config.getTcsGstin().equalsIgnoreCase(config.getGstin())) {
                throw new IllegalArgumentException("The TCS registration cannot be the same as the "
                        + "regular GSTIN. Section 52 registration is a separate GSTIN, and GSTR-8 "
                        + "filed under the regular one is rejected.");
            }
        }

        if (!isBlank(config.getCin()) && !GstComplianceUtil.isValidCin(config.getCin())) {
            throw new IllegalArgumentException("'" + config.getCin() + "' is not a valid CIN. It is "
                    + "21 characters: L or U, 5-digit industry code, 2-letter state, 4-digit year, "
                    + "3-letter ownership code, then a 6-digit registration number.");
        }
        if (!isBlank(config.getNodalIfsc()) && !GstComplianceUtil.isValidIfsc(config.getNodalIfsc())) {
            throw new IllegalArgumentException("'" + config.getNodalIfsc() + "' is not a valid IFSC. "
                    + "It is 11 characters: 4-letter bank code, a zero, then the branch code.");
        }
        if (config.getCommissionGstRate() != null
                && (config.getCommissionGstRate() < 0 || config.getCommissionGstRate() > 100)) {
            throw new IllegalArgumentException("The commission GST rate must be between 0 and 100.");
        }
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    /**
     * The state's name for the place-of-supply field, from the official master.
     *
     * Read from state_master rather than a map kept in this file. The hand-maintained one had
     * drifted from the published list: it still carried 28 for Andhra Pradesh, which was the
     * code before Telangana was carved out, and lacked 37 - the code Andhra Pradesh actually
     * uses now. An invoice to an Andhra customer therefore read "37-Other". It also lacked
     * Ladakh, and the 96/97/99 codes used for exports.
     *
     * Refuses rather than falling back to "Other". Place of supply is a mandatory field on a
     * tax invoice and decides which government is owed the money; "Other" is not a state, and
     * an invoice carrying it is wrong in a way nobody would notice.
     */
    private String stateName(String stateCode) {
        if (stateCode == null || stateCode.isBlank()) {
            throw new IllegalStateException(
                    "This order has no delivery state, so the invoice has no place of supply. "
                            + "Place of supply decides whether CGST+SGST or IGST applies, so the "
                            + "invoice cannot be raised without it.");
        }
        String code = stateCode.trim().length() == 1 ? "0" + stateCode.trim() : stateCode.trim();
        return stateRepo.findById(code)
                .map(StateMaster::getStateName)
                .orElseThrow(() -> new IllegalStateException(
                        "'" + stateCode + "' is not a GST state code in the official master, so the "
                                + "place of supply cannot be stated. Check the delivery address."));
    }

    public Map<String, Object> getDashboardStats() {
        long total = invoiceRepo.count();
        long synced = invoiceRepo.countByStatus("SYNCED");
        long pending = invoiceRepo.countByStatus("GENERATED");
        long failed = invoiceRepo.countByStatus("SYNC_FAILED");
        long queueSize = syncRepo.countByStatus("PENDING");

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalInvoices", total);
        stats.put("syncedToGstn", synced);
        stats.put("pendingSync", pending);
        stats.put("syncFailed", failed);
        stats.put("queueSize", queueSize);
        return stats;
    }

    public List<Map<String, String>> getStateCodes() {
        List<Map<String, String>> list = new ArrayList<>();
        for (StateMaster s : stateRepo.findAll()) {
            list.add(Map.of("code", s.getStateCode(), "name", s.getStateName()));
        }
        return list;
    }

    private String generateInvoiceNumber(String prefix) {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM"));
        String safePrefix = prefix != null ? prefix.trim().toUpperCase().replaceAll("[^A-Z0-9]", "") : "CS";
        if (safePrefix.isEmpty()) safePrefix = "CS";
        // GSTIN invoice numbers must not exceed 16 chars: prefix (<=9) + yyMM (4) + "/" (1) + seq (5) = 16
        if (safePrefix.length() > 9) safePrefix = safePrefix.substring(0, 9);
        String prefixPattern = safePrefix + datePart + "/";
        String maxNum = invoiceRepo.findMaxInvoiceNumberByPrefix(prefixPattern);
        int seq = 1;
        if (maxNum != null) {
            String[] parts = maxNum.split("/");
            seq = Integer.parseInt(parts[parts.length - 1]) + 1;
        }
        return prefixPattern + String.format("%05d", seq);
    }

    /**
     * The GST state code for a state name from a delivery address.
     *
     * Used to be a lookup that returned "33" - Tamil Nadu - for anything it did not recognise,
     * including null. A single unrecognised spelling therefore made an inter-state supply look
     * intra-state, charging CGST+SGST where IGST was due and sending the money to the wrong
     * government. It now refuses instead.
     *
     * Matching ignores case, punctuation and "and" versus "&", because an address typed by a
     * customer will not match the gazette spelling exactly and rejecting it on that would be
     * pedantry rather than compliance.
     */
    private String getStateCode(String stateName) {
        if (stateName == null || stateName.isBlank()) {
            throw new IllegalStateException(
                    "The delivery address has no state, so the place of supply cannot be "
                            + "determined and the invoice cannot state whether IGST or CGST+SGST "
                            + "applies.");
        }
        String wanted = normaliseStateName(stateName);
        for (StateMaster s : stateRepo.findAll()) {
            if (normaliseStateName(s.getStateName()).equals(wanted)) return s.getStateCode();
        }
        throw new IllegalStateException("'" + stateName + "' is not a state in the official GST "
                + "master, so the place of supply cannot be determined. Correct the delivery "
                + "address.");
    }

    /**
     * The state code for a delivery address, from the state name or, failing that, the pincode.
     *
     * The pincode fallback is the point of holding the range map: place of supply must be right,
     * and an address whose state is mistyped or missing would otherwise refuse the sale outright.
     * The pincode sits on the same address, is six digits with no spelling to get wrong, and
     * names the state on its own.
     *
     * When both are present and disagree, the typed state wins and the mismatch is logged. A
     * pincode can legitimately sit in two states' published ranges - 396 belongs to both Gujarat
     * and Dadra and Nagar Haveli - so treating a disagreement as proof of error would reject
     * correct addresses.
     */
    public String resolveDeliveryState(Address addr) {
        if (addr == null) {
            throw new IllegalStateException(
                    "This order has no delivery address, so there is no place of supply and the "
                            + "invoice cannot say whether IGST or CGST+SGST applies.");
        }
        String fromPincode = stateCodeFromPincode(addr.getPincode());

        try {
            String fromName = getStateCode(addr.getState());
            if (fromPincode != null && !fromPincode.equals(fromName)) {
                log.warn("Delivery address states '{}' (code {}) but its pincode {} belongs to "
                                + "state {}. Using the stated state; check the address if the "
                                + "invoice looks wrong.",
                        addr.getState(), fromName, addr.getPincode(), fromPincode);
            }
            return fromName;
        } catch (IllegalStateException stateUnresolvable) {
            if (fromPincode != null) {
                log.info("Delivery state '{}' could not be matched, so it was taken from pincode "
                        + "{} as state {}.", addr.getState(), addr.getPincode(), fromPincode);
                return fromPincode;
            }
            throw stateUnresolvable;
        }
    }

    /**
     * The state a pincode belongs to, or null when it is missing, unrecognised, or shared by
     * more than one state - in which case it settles nothing and should not pretend to.
     */
    public String stateCodeFromPincode(String pincode) {
        if (pincode == null || pincode.isBlank()) return null;
        String digits = pincode.replaceAll("\\D", "");
        if (digits.length() < 3) return null;
        int prefix = Integer.parseInt(digits.substring(0, 3));

        java.util.Set<String> matches = pincodeRepo
                .findByPrefixFromLessThanEqualAndPrefixToGreaterThanEqual(prefix, prefix)
                .stream().map(PincodeStateRange::getStateCode)
                .collect(java.util.stream.Collectors.toSet());
        return matches.size() == 1 ? matches.iterator().next() : null;
    }

    private String normaliseStateName(String name) {
        return name == null ? "" : name.trim().toUpperCase()
                .replace("&", "AND")
                .replaceAll("[^A-Z]", "");
    }

    private String formatAddress(Address addr) {
        if (addr == null) return "";
        return String.join(", ",
            addr.getStreet() != null ? addr.getStreet() : "",
            addr.getCity() != null ? addr.getCity() : "",
            addr.getState() != null ? addr.getState() : "",
            addr.getPincode() != null ? addr.getPincode() : ""
        ).replaceAll("^,\\s*|,\\s*$", "").replaceAll(",\\s*,", ",");
    }

    private String sha256Digest(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString().toUpperCase();
        } catch (Exception e) {
            return null;
        }
    }

    public byte[] exportExcel(String sheetName, String[] headers, List<Map<String, Object>> rows) {
        return ExcelExportUtil.toExcel(sheetName, headers, rows);
    }

    public Map<String, Object> getCustomerInvoiceForOrder(Long orderId, Long customerUserId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        User orderUser = order.getUser();
        boolean ownsOrder = orderUser != null && orderUser.getId().equals(customerUserId);
        if (!ownsOrder && orderUser != null) {
            User currentUser = userRepo.findById(customerUserId).orElse(null);
            ownsOrder = currentUser != null && orderUser.getEmail() != null
                    && orderUser.getEmail().equalsIgnoreCase(currentUser.getEmail());
        }
        if (!ownsOrder) {
            throw new RuntimeException("Invoice not available for this order");
        }
        GstInvoice inv = invoiceRepo.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Invoice not yet generated for this order"));
        List<GstInvoiceItem> items = itemRepo.findByInvoiceId(inv.getId());
        inv.setItems(items);
        return Map.of("invoice", inv, "orderStatus", order.getStatus());
    }

    @Transactional
    public void updateInvoiceStatusByOrderId(Long orderId, String dispatchStatus) {
        invoiceRepo.findByOrderId(orderId).ifPresent(inv -> {
            inv.setStatus(dispatchStatus);
            if ("DISPATCHED".equals(dispatchStatus) || "DELIVERED".equals(dispatchStatus)) {
                inv.setInvoiceStatus("FINALIZED");
            }
            invoiceRepo.save(inv);
        });
    }

    public byte[] generateInvoicePdf(Long invoiceId) throws DocumentException {
        GstInvoice inv = invoiceRepo.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + invoiceId));
        List<GstInvoiceItem> items = itemRepo.findByInvoiceId(invoiceId);
        inv.setItems(items);

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

        boolean interState = Boolean.TRUE.equals(inv.getIsInterState());
        boolean utgst = Boolean.TRUE.equals(inv.getUtgstApplied());
        String stateLabel = utgst ? "UTGST" : "SGST";

        // Brand header (logo + "Cauvery Store - Everyday Essentials, Delivered")
        doc.add(PdfBrandingUtil.buildBrandHeader());
        doc.add(PdfBrandingUtil.buildDivider());
        doc.add(new Paragraph(" "));

        // Header
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{60, 40});
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.addElement(new Paragraph("Tax Invoice", bold14));
        leftCell.addElement(new Paragraph(
                (inv.getInvoiceCopyType() != null && inv.getInvoiceCopyType().equals("ORIGINAL") ? "Original for Recipient" :
                 "ORIGINAL".equals(inv.getInvoiceCopyType()) ? "Original for Recipient" :
                 "DUPLICATE".equals(inv.getInvoiceCopyType()) ? "Duplicate for Transporter" :
                 "TRIPLICATE".equals(inv.getInvoiceCopyType()) ? "Triplicate for Supplier" : "Original for Recipient")
                + (inv.getSupplyType() != null && inv.getSupplyType().equals("GOODS") ? " (Goods)" : " (Services)")
                + " | " + (interState ? "Inter-State" : "Intra-State"), small8));
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(new Paragraph("Invoice No.", small8));
        rightCell.addElement(new Paragraph(safeStr(inv.getInvoiceNumber()), bold12));
        rightCell.addElement(new Paragraph("Invoice Date", small8));
        rightCell.addElement(new Paragraph(inv.getInvoiceDate() != null ? inv.getInvoiceDate().toString() : "", normal9));
        rightCell.addElement(new Paragraph("Status: " + inv.getStatus(), small8));
        headerTable.addCell(leftCell);
        headerTable.addCell(rightCell);
        doc.add(headerTable);

        doc.add(new Paragraph(" "));

        // Seller / Buyer info
        PdfPTable parties = new PdfPTable(2);
        parties.setWidthPercentage(100);
        parties.setWidths(new float[]{50, 50});

        PdfPCell sellCell = new PdfPCell();
        sellCell.setPadding(8);
        sellCell.addElement(new Paragraph("Seller (Supplier)", bold10));
        sellCell.addElement(new Paragraph(safeStr(inv.getSellerLegalName()), normal9));
        sellCell.addElement(new Paragraph("GSTIN: " + safeStr(inv.getSellerGstin()), normal9));
        sellCell.addElement(new Paragraph("Address: " + safeStr(inv.getSellerAddress()), small8));
        parties.addCell(sellCell);

        PdfPCell buyCell = new PdfPCell();
        buyCell.setPadding(8);
        buyCell.addElement(new Paragraph("Buyer / Recipient", bold10));
        buyCell.addElement(new Paragraph(safeStr(inv.getBuyerName()), normal9));
        buyCell.addElement(new Paragraph("GSTIN: " + (inv.getBuyerGstin() != null ? inv.getBuyerGstin() : "URP")
                + (inv.getBuyerGstin() != null && !"URP".equals(inv.getBuyerGstin()) ? " (ITC Eligible: Yes - B2B)" : " (URP)"), normal9));
        buyCell.addElement(new Paragraph("Address: " + safeStr(inv.getBuyerAddress()), small8));
        buyCell.addElement(new Paragraph("State Code: " + safeStr(inv.getBuyerStateCode()), small8));
        if (inv.getDeliveryAddress() != null) {
            buyCell.addElement(new Paragraph("Ship To (Delivery):", bold10));
            buyCell.addElement(new Paragraph(safeStr(inv.getDeliveryAddress()), small8));
            buyCell.addElement(new Paragraph("Delivery State: " + safeStr(inv.getDeliveryStateCode()), small8));
        }
        parties.addCell(buyCell);
        doc.add(parties);

        doc.add(new Paragraph(" "));

        // Identifiers section
        PdfPTable idTable = new PdfPTable(3);
        idTable.setWidthPercentage(100);
        float[] idW = {33, 33, 34};
        idTable.setWidths(idW);
        String[][] idData = {
            {"Place of Supply", safeStr(inv.getPlaceOfSupply())},
            {"Supply Type", interState ? "Inter-State (IGST)"
                    : "Intra-State (" + (utgst ? "CGST+UTGST" : "CGST+SGST") + ")"},
            {"Invoice Type", safeStr(inv.getInvoiceType())}
        };
        for (String[] row : idData) {
            PdfPCell c = new PdfPCell();
            c.setPadding(4);
            c.addElement(new Paragraph(row[0], small8));
            c.addElement(new Paragraph(row[1], bold9));
            idTable.addCell(c);
        }
        doc.add(idTable);

        doc.add(new Paragraph(" "));

        // Items table - match web view columns (separate CGST/SGST)
        int numColumns = Boolean.TRUE.equals(inv.getIsInterState()) ? 8 : 9;
        PdfPTable table = new PdfPTable(numColumns);
        table.setWidthPercentage(100);
        float[] colWidths = Boolean.TRUE.equals(inv.getIsInterState())
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
        for (GstInvoiceItem item : items) {
            table.addCell(new Phrase(String.valueOf(sn++), normal9));
            table.addCell(new Phrase(safeStr(item.getSacCode() != null ? item.getSacCode() : item.getHsnCode()), small8));
            table.addCell(new Phrase(safeStr(item.getProductName()), normal9));
            table.addCell(new Phrase(String.valueOf(item.getQuantity()), normal9));
            table.addCell(new Phrase("\u20B9" + String.format("%.2f", item.getUnitPrice() != null ? item.getUnitPrice() : 0), normal9));
            table.addCell(new Phrase("\u20B9" + String.format("%.2f", item.getTaxableValue() != null ? item.getTaxableValue() : 0), normal9));
            if (Boolean.TRUE.equals(inv.getIsInterState())) {
                table.addCell(new Phrase((item.getIgstRate() != null ? item.getIgstRate() : 0) + "% / \u20B9" + String.format("%.2f", item.getIgstAmount() != null ? item.getIgstAmount() : 0), small8));
            } else {
                table.addCell(new Phrase((item.getCgstRate() != null ? item.getCgstRate() : 0) + "% / \u20B9" + String.format("%.2f", item.getCgstAmount() != null ? item.getCgstAmount() : 0), small8));
                table.addCell(new Phrase((item.getSgstRate() != null ? item.getSgstRate() : 0) + "% / \u20B9" + String.format("%.2f", item.getSgstAmount() != null ? item.getSgstAmount() : 0), small8));
            }
            table.addCell(new Phrase("\u20B9" + String.format("%.2f", item.getTotalAmount() != null ? item.getTotalAmount() : 0), bold9));
        }
        doc.add(table);
        doc.add(new Paragraph(" "));

        // Tax Breakup section
        PdfPTable breakupTable = new PdfPTable(4);
        breakupTable.setWidthPercentage(65);
        breakupTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        float[] bw = {25, 25, 25, 25};
        breakupTable.setWidths(bw);

        float tx = inv.getTaxableAmount() != null ? inv.getTaxableAmount().floatValue() : 0f;
        double cgst = inv.getCgstAmount() != null ? inv.getCgstAmount() : 0;
        double sgst = inv.getSgstAmount() != null ? inv.getSgstAmount() : 0;
        double igst = inv.getIgstAmount() != null ? inv.getIgstAmount() : 0;
        double ttax = inv.getTotalTax() != null ? inv.getTotalTax() : 0;
        double tcs = inv.getTcsAmount() != null ? inv.getTcsAmount() : 0;
        double total = inv.getTotalAmount() != null ? inv.getTotalAmount() : 0;
        double cgstRate = inv.getCgstRate() != null ? inv.getCgstRate() : 0;
        double sgstRate = inv.getSgstRate() != null ? inv.getSgstRate() : 0;
        double igstRate = !items.isEmpty() && items.get(0).getIgstRate() != null ? items.get(0).getIgstRate() : 0;
        double tcsRate = inv.getTcsRate() != null ? inv.getTcsRate() : 1.0;

        // Header row
        PdfPCell bh = new PdfPCell(new Phrase("Tax Breakup", bold10));
        bh.setColspan(4);
        bh.setBackgroundColor(PdfBrandingUtil.TEAL);
        bh.setPadding(6);
        bh.setHorizontalAlignment(Element.ALIGN_CENTER);
        breakupTable.addCell(bh);

        addBreakupCell(breakupTable, "Taxable Amount", "\u20B9" + String.format("%.2f", tx), normal9);
        if (inv.getDiscountAmount() != null && inv.getDiscountAmount() > 0) {
            addBreakupCell(breakupTable, "Discount", "-\u20B9" + String.format("%.2f", inv.getDiscountAmount()), normal9);
        }
        if (inv.getDeliveryCharge() != null && inv.getDeliveryCharge() > 0) {
            addBreakupCell(breakupTable, "Delivery Charge", "\u20B9" + String.format("%.2f", inv.getDeliveryCharge()), normal9);
        }
        if (interState) {
            addBreakupCell(breakupTable, "IGST @ " + igstRate + "%", "\u20B9" + String.format("%.2f", igst), normal9);
        } else {
            addBreakupCell(breakupTable, "CGST @ " + cgstRate + "%", "\u20B9" + String.format("%.2f", cgst), normal9);
            addBreakupCell(breakupTable, stateLabel + " @ " + sgstRate + "%", "\u20B9" + String.format("%.2f", sgst), normal9);
        }
        addBreakupCell(breakupTable, "Total Tax", "\u20B9" + String.format("%.2f", ttax), normal9);
        // TCS is deliberately not in this breakup. It is not a tax on this supply and the
        // customer does not pay it - printing it beside CGST and SGST read as though they did.
        doc.add(breakupTable);

        doc.add(new Paragraph(" "));

        // Summary table
        PdfPTable sumTable = new PdfPTable(2);
        sumTable.setWidthPercentage(40);
        sumTable.setHorizontalAlignment(Element.ALIGN_RIGHT);

        addSumRow(sumTable, "Taxable Amount", "\u20B9" + String.format("%.2f", tx), normal9);
        if (inv.getDiscountAmount() != null && inv.getDiscountAmount() > 0) {
            addSumRow(sumTable, "Discount", "-\u20B9" + String.format("%.2f", inv.getDiscountAmount()), normal9);
        }
        if (inv.getDeliveryCharge() != null && inv.getDeliveryCharge() > 0) {
            addSumRow(sumTable, "Delivery Charge", "\u20B9" + String.format("%.2f", inv.getDeliveryCharge()), normal9);
        }
        if (interState) {
            addSumRow(sumTable, "IGST", "\u20B9" + String.format("%.2f", igst), normal9);
        } else {
            addSumRow(sumTable, "CGST", "\u20B9" + String.format("%.2f", cgst), normal9);
            addSumRow(sumTable, stateLabel, "\u20B9" + String.format("%.2f", sgst), normal9);
        }
        addSumRow(sumTable, "Total Tax", "\u20B9" + String.format("%.2f", ttax), normal9);
        // TCS sat here, between Total Tax and Total Amount, which is exactly where a customer
        // reads a charge as theirs - and it was being added into the total. It belongs below
        // the total as a note about the marketplace's own obligation, not in the sum.
        PdfPCell totalCell = new PdfPCell(new Phrase("Total Amount", bold12));
        totalCell.setBorder(Rectangle.TOP);
        sumTable.addCell(totalCell);
        PdfPCell totalVal = new PdfPCell(new Phrase("\u20B9" + String.format("%.2f", total), bold12));
        totalVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalVal.setBorder(Rectangle.TOP);
        sumTable.addCell(totalVal);
        doc.add(sumTable);

        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Amount in Words: " + numberToWordsPdf(total), bold9));

        // Stated, but plainly as the marketplace's obligation rather than a line on the bill.
        // Section 52 has the operator keep this back from what the seller is paid; the customer
        // is not charged it and the figure above does not include it.
        if (tcs > 0) {
            doc.add(new Paragraph("TCS u/s 52: ₹" + String.format("%.2f", tcs) + " (" + tcsRate
                    + "% of taxable value) is collected by the marketplace from the seller's "
                    + "settlement and declared in GSTR-8. It is not charged to the buyer and is "
                    + "not included in the Total Amount above.", small8));
        }

        // Payment terms. An invoice that does not say whether it has been paid is one the
        // buyer's accounts team has to ring the seller about.
        if (inv.getPaymentMode() != null && !inv.getPaymentMode().isBlank()) {
            String terms = "Payment Mode: " + inv.getPaymentMode();
            terms += inv.getPaymentDueDate() != null
                    ? "   |   Due: " + inv.getPaymentDueDate() + " (payable on delivery)"
                    : "   |   Paid in full - no amount outstanding";
            doc.add(new Paragraph(terms, small8));
        }

        // IRN / QR section. A bare "IRN: ..." reads as a genuine portal reference wherever it
        // appears, so a simulated one is labelled at the point it is printed rather than only
        // in the declaration further down.
        if (inv.getIrn() != null) {
            boolean simulated = inv.getIrn().startsWith("SIM");
            doc.add(new Paragraph((simulated ? "Reference (SIMULATED - not an IRP registration): "
                    : "IRN: ") + inv.getIrn(), small8));
            if (inv.getEwayBillNumber() != null) {
                doc.add(new Paragraph("E-Way Bill: " + inv.getEwayBillNumber() + (inv.getEwayBillExpiry() != null ? " (exp: " + inv.getEwayBillExpiry() + ")" : ""), small8));
            }
            if (inv.getAckNo() != null) doc.add(new Paragraph("Ack No: " + inv.getAckNo() + "  Date: " + safeStr(inv.getAckDate()), small8));
        }

        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Declaration (Rule 46 CGST Rules, 2017): We declare that this invoice shows the actual price of the goods/services described and that all particulars are true and correct.", small8));
        doc.add(new Paragraph("- This is a computer-generated " + (inv.getInvoiceCopyType() != null ? inv.getInvoiceCopyType().toLowerCase() : "original") + " invoice for " + (inv.getSupplyType() != null && inv.getSupplyType().equals("GOODS") ? "goods" : "services") + " as per Rule 46.", small8));
        doc.add(new Paragraph("- " + (interState ? "IGST charged (inter-state supply)."
                : utgst ? "CGST + UTGST charged (intra-state supply from a Union Territory)."
                        : "CGST + SGST charged (intra-state supply)."), small8));
        doc.add(new Paragraph("- HSN/SAC digits: " + (inv.getHsnDigits() != null ? inv.getHsnDigits() : 4) + ".", small8));
        if (inv.getSupplierSignature() != null) {
            doc.add(new Paragraph("- Digitally authenticated (SHA-256) and authorized by " + safeStr(inv.getSignedBy())
                    + (inv.getSignatureDate() != null ? " on " + inv.getSignatureDate() : "") + ".", small8));
            doc.add(new Paragraph("Signature Digest (SHA-256): " + inv.getSupplierSignature(), small8));
        }

        // Rule 46(q) asks for the signature of the supplier or their authorised representative.
        // The digest above proves the invoice has not been altered; it is not that signature.
        // An e-invoice carrying an IRN needs neither, because the portal signs it - so say so
        // rather than printing a "no signature required" line that is only true in that case.
        // Only a real IRP registration may be claimed. The simulator issues IRNs prefixed
        // "SIM", and printing "registered on the e-invoice portal, digitally signed by the
        // portal" over one of those is a false statement on a tax document - made worse by the
        // fact that the claim was being used to excuse the missing signature. A simulated
        // invoice therefore says so, and still needs signing like any other.
        boolean simulatedIrn = inv.getIrn() != null && inv.getIrn().startsWith("SIM");
        boolean eInvoiced = inv.getIrn() != null && !inv.getIrn().isBlank() && !simulatedIrn;
        String signatureImage = inv.getSupplierSignatureImageUrl();
        if (simulatedIrn) {
            doc.add(new Paragraph("- NOT registered on the e-invoice portal. The reference above "
                    + "was generated locally for testing and has no standing with GSTN.", small8));
        }
        if (eInvoiced) {
            doc.add(new Paragraph("- Registered on the e-invoice portal (IRN " + inv.getIrn()
                    + "). Digitally signed by the portal; no physical signature is required.", small8));
        } else if (signatureImage != null && !signatureImage.isBlank()) {
            doc.add(new Paragraph(" "));
            try {
                Image signature = Image.getInstance(new java.net.URL(signatureImage));
                signature.scaleToFit(140f, 55f);
                signature.setAlignment(Element.ALIGN_RIGHT);
                doc.add(signature);
            } catch (Exception e) {
                // The invoice still has to print. Say the signature is on file but could not be
                // rendered, rather than leaving a silent gap that reads as unsigned.
                log.warn("Could not render the signature on invoice {}: {}",
                        inv.getInvoiceNumber(), e.getMessage());
                doc.add(new Paragraph("[Signature on file could not be rendered]", small8));
            }
            Paragraph signatory = new Paragraph("For " + safeStr(inv.getSignedBy())
                    + ", Authorised Signatory"
                    + (inv.getSignatureDate() != null ? " | " + inv.getSignatureDate() : ""), bold9);
            signatory.setAlignment(Element.ALIGN_RIGHT);
            doc.add(signatory);
        } else {
            doc.add(new Paragraph("- No signature is on file for this seller. Rule 46 requires the "
                    + "supplier's signature on a tax invoice unless it is an e-invoice signed by "
                    + "the portal.", small8));
            Paragraph signatory = new Paragraph("For " + safeStr(inv.getSignedBy())
                    + ", Authorised Signatory", bold9);
            signatory.setAlignment(Element.ALIGN_RIGHT);
            doc.add(signatory);
        }

        doc.close();
        return baos.toByteArray();
    }

    /**
     * Refuses to date an invoice before the seller's books open.
     *
     * An invoice earlier than that belongs to a period whose return may already be filed, or
     * to no accounting period at all - either way it cannot be reported honestly, and the
     * problem only surfaces when the figures fail to reconcile.
     *
     * A seller who has not set the date is let through. Most of them registered before the
     * field existed, and refusing to invoice their orders over a missing profile field would
     * stop real sales to enforce a rule they were never asked about.
     */
    private void assertBooksAreOpen(Long sellerId, LocalDate invoiceDate) {
        if (sellerId == null || invoiceDate == null) return;
        sellerRegRepo.findByUserId(sellerId).ifPresent(reg -> {
            LocalDate booksOpen = reg.getBooksBeginningDate();
            if (booksOpen != null && invoiceDate.isBefore(booksOpen)) {
                throw new IllegalStateException(
                        "This invoice would be dated " + invoiceDate + ", before the seller's books "
                                + "open on " + booksOpen + ". It would fall in a period that cannot "
                                + "be reported. Correct the books beginning date on the seller "
                                + "profile if it is wrong.");
            }
        });
    }

    private void addBreakupCell(PdfPTable table, String label, String value, Font font) {
        PdfPCell c = new PdfPCell();
        c.setPadding(4);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.addElement(new Paragraph(label, new Font(Font.HELVETICA, 8)));
        c.addElement(new Paragraph(value, font));
        table.addCell(c);
    }

    private String safeStr(String s) {
        return s != null ? s : "";
    }

    private double nz(Double v) {
        return v != null ? v : 0.0;
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
