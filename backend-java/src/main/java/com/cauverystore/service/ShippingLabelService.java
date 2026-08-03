package com.cauverystore.service;

import com.cauverystore.entities.Address;
import com.cauverystore.entities.Order;
import com.cauverystore.entities.OrderItem;
import com.cauverystore.entities.SellerRegistration;
import com.cauverystore.repository.OrderRepository;
import com.cauverystore.repository.SellerRegistrationRepository;
import com.cauverystore.util.PdfBrandingUtil;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.Barcode128;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShippingLabelService {

    private static final Logger log = LoggerFactory.getLogger(ShippingLabelService.class);

    // Statuses a courier label makes no sense for - nothing to ship, or nothing to ship yet.
    private static final List<String> UNLABELABLE_STATUSES = List.of("CANCELLED", "REFUNDED", "RETURNED");

    private final OrderRepository orderRepo;
    private final SellerRegistrationRepository sellerRegRepo;
    private final AuditService auditService;
    private final AuthorizationService authorizationService;

    public byte[] generateLabelPdf(Long orderId) throws DocumentException {
        return generateLabelPdf(orderId, null);
    }

    /** When requestingSellerId is non-null, only that seller's own orders can be labeled. */
    public byte[] generateLabelPdf(Long orderId, Long requestingSellerId) throws DocumentException {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        if (requestingSellerId != null && !requestingSellerId.equals(order.getSellerId())) {
            throw new RuntimeException("Access denied: this order does not belong to you");
        }
        if (UNLABELABLE_STATUSES.contains(order.getStatus())) {
            throw new RuntimeException("Cannot generate a shipping label for an order that is " + order.getStatus());
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Standard 4x6 inch courier label (72pt/inch), portrait - the thermal-printer default.
        Document doc = new Document(new Rectangle(4 * 72f, 6 * 72f), 14, 14, 14, 14);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        doc.open();

        Font bold14 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        Font bold11 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
        Font bold9 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        Font normal9 = FontFactory.getFont(FontFactory.HELVETICA, 9);
        Font small8 = FontFactory.getFont(FontFactory.HELVETICA, 8);

        doc.add(PdfBrandingUtil.buildBrandHeader());
        doc.add(PdfBrandingUtil.buildDivider());
        doc.add(new Paragraph(" "));

        // COD badge, in brand red, only when relevant
        if ("COD".equalsIgnoreCase(order.getPaymentMethod())) {
            PdfPTable codBadge = new PdfPTable(1);
            codBadge.setWidthPercentage(100);
            PdfPCell codCell = new PdfPCell(new Phrase("CASH ON DELIVERY", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.WHITE)));
            codCell.setBackgroundColor(PdfBrandingUtil.RED);
            codCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            codCell.setPadding(5);
            codBadge.addCell(codCell);
            doc.add(codBadge);
            doc.add(new Paragraph(" "));
        }

        // Ship From (Return Address) / Ship To
        PdfPTable addresses = new PdfPTable(2);
        addresses.setWidthPercentage(100);
        addresses.setWidths(new float[]{50, 50});

        SellerRegistration seller = order.getSellerId() != null
                ? sellerRegRepo.findByUserId(order.getSellerId()).orElse(null)
                : null;

        PdfPCell fromCell = new PdfPCell();
        fromCell.setPadding(6);
        fromCell.setBackgroundColor(PdfBrandingUtil.BEIGE);
        fromCell.addElement(new Paragraph("RETURN ADDRESS", bold9));
        if (seller != null) {
            fromCell.addElement(new Paragraph(safe(seller.getBusinessName()), normal9));
            fromCell.addElement(new Paragraph(safe(seller.getBusinessAddress()), small8));
            fromCell.addElement(new Paragraph("Ph: " + safe(seller.getBusinessPhone()), small8));
        } else {
            fromCell.addElement(new Paragraph("Cauvery Store Fulfillment Center", normal9));
        }
        addresses.addCell(fromCell);

        PdfPCell toCell = new PdfPCell();
        toCell.setPadding(6);
        Address addr = order.getAddress();
        toCell.addElement(new Paragraph("SHIP TO", bold9));
        if (addr != null) {
            toCell.addElement(new Paragraph(safe(addr.getFullName()), bold11));
            toCell.addElement(new Paragraph(safe(addr.getStreet()), normal9));
            toCell.addElement(new Paragraph(safe(addr.getCity()) + ", " + safe(addr.getState()) + " - " + safe(addr.getPincode()), normal9));
            toCell.addElement(new Paragraph("Ph: " + safe(addr.getPhone()), small8));
        } else {
            toCell.addElement(new Paragraph("Address not available", normal9));
        }
        addresses.addCell(toCell);
        doc.add(addresses);

        doc.add(new Paragraph(" "));

        // Order ID / Service type
        PdfPTable idTable = new PdfPTable(2);
        idTable.setWidthPercentage(100);
        idTable.setWidths(new float[]{50, 50});
        addIdCell(idTable, "Order ID", "#" + order.getId(), bold9, bold14);
        String serviceType = "COD".equalsIgnoreCase(order.getPaymentMethod()) ? "COD" : "Prepaid - Standard";
        addIdCell(idTable, "Service Type", serviceType, bold9, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11));
        doc.add(idTable);

        doc.add(new Paragraph(" "));

        // Itemized product list
        List<OrderItem> items = order.getItems();
        PdfPTable itemsTable = new PdfPTable(2);
        itemsTable.setWidthPercentage(100);
        itemsTable.setWidths(new float[]{75, 25});
        Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        PdfPCell itemHeader1 = new PdfPCell(new Phrase("Product", tableHeaderFont));
        itemHeader1.setBackgroundColor(PdfBrandingUtil.TEAL);
        itemHeader1.setPadding(3);
        PdfPCell itemHeader2 = new PdfPCell(new Phrase("Qty", tableHeaderFont));
        itemHeader2.setBackgroundColor(PdfBrandingUtil.TEAL);
        itemHeader2.setPadding(3);
        itemHeader2.setHorizontalAlignment(Element.ALIGN_CENTER);
        itemsTable.addCell(itemHeader1);
        itemsTable.addCell(itemHeader2);

        double totalWeight = 0;
        boolean anyWeight = false;
        boolean anyFragile = false;
        if (items != null) {
            for (OrderItem item : items) {
                String name = item.getProduct() != null ? item.getProduct().getName() : "Item";
                PdfPCell nameCell = new PdfPCell(new Phrase(safe(name), small8));
                nameCell.setPadding(3);
                itemsTable.addCell(nameCell);
                PdfPCell qtyCell = new PdfPCell(new Phrase(String.valueOf(item.getQuantity()), small8));
                qtyCell.setPadding(3);
                qtyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                itemsTable.addCell(qtyCell);

                if (item.getProduct() != null) {
                    Double w = item.getProduct().getPackageWeight() != null ? item.getProduct().getPackageWeight() : item.getProduct().getWeight();
                    if (w != null) {
                        totalWeight += w * item.getQuantity();
                        anyWeight = true;
                    }
                    String shippingClass = item.getProduct().getShippingClass();
                    if (shippingClass != null && shippingClass.toLowerCase().contains("fragile")) {
                        anyFragile = true;
                    }
                }
            }
        }
        doc.add(itemsTable);

        if (anyWeight) {
            Paragraph weightLine = new Paragraph("Package Weight: " + String.format("%.2f kg", totalWeight), normal9);
            weightLine.setSpacingBefore(4);
            doc.add(weightLine);
        }

        // Shipping instructions (optional)
        String deliveryInstructions = addr != null ? addr.getDeliveryInstructions() : null;
        if (anyFragile || (deliveryInstructions != null && !deliveryInstructions.isBlank())) {
            doc.add(new Paragraph(" "));
            PdfPTable instrTable = new PdfPTable(1);
            instrTable.setWidthPercentage(100);
            PdfPCell instrCell = new PdfPCell();
            instrCell.setPadding(5);
            instrCell.setBorderColor(PdfBrandingUtil.RED);
            instrCell.setBorderWidth(1f);
            StringBuilder instr = new StringBuilder();
            if (anyFragile) instr.append("FRAGILE - HANDLE WITH CARE");
            if (deliveryInstructions != null && !deliveryInstructions.isBlank()) {
                if (instr.length() > 0) instr.append("  |  ");
                instr.append(deliveryInstructions);
            }
            instrCell.addElement(new Paragraph(instr.toString(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, PdfBrandingUtil.RED)));
            instrTable.addCell(instrCell);
            doc.add(instrTable);
        }

        doc.add(new Paragraph(" "));

        // Barcodes - order barcode for internal tracking, tracking barcode for courier scanning
        PdfContentByte cb = writer.getDirectContent();
        PdfPTable barcodeTable = new PdfPTable(2);
        barcodeTable.setWidthPercentage(100);
        barcodeTable.setWidths(new float[]{50, 50});

        barcodeTable.addCell(buildBarcodeCell(cb, "ORDER BARCODE", "ORD" + order.getId(), bold9, small8));

        String tracking = order.getTrackingNumber();
        String courier = order.getCourier() != null ? order.getCourier() : "Not assigned";
        if (tracking != null && !tracking.isBlank()) {
            barcodeTable.addCell(buildBarcodeCell(cb, "COURIER: " + courier, tracking, bold9, small8));
        } else {
            PdfPCell pendingCell = new PdfPCell();
            pendingCell.setPadding(6);
            pendingCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            pendingCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            pendingCell.addElement(new Paragraph("TRACKING", bold9));
            pendingCell.addElement(new Paragraph("Awaiting courier assignment", small8));
            barcodeTable.addCell(pendingCell);
        }
        doc.add(barcodeTable);

        doc.close();
        byte[] pdfBytes = out.toByteArray();

        recordLabelGenerated(order);

        return pdfBytes;
    }

    /** Persists that this label was generated and logs it, without letting a failure here block the PDF that was already built. */
    private void recordLabelGenerated(Order order) {
        try {
            order.setLabelGeneratedAt(LocalDateTime.now());
            orderRepo.save(order);
            Long actorId = authorizationService.getCurrentUserId();
            String actorEmail = authorizationService.getCurrentUserEmail();
            auditService.log(actorId, actorEmail, "SHIPPING_LABEL_GENERATED", "Order", order.getId(),
                    "Shipping label generated for order #" + order.getId(), null);
        } catch (Exception e) {
            log.warn("Failed to record shipping label generation for order {}: {}", order.getId(), e.getMessage());
        }
    }

    private PdfPCell buildBarcodeCell(PdfContentByte cb, String label, String code, Font labelFont, Font smallFont) {
        Barcode128 barcode = new Barcode128();
        barcode.setCode(code);
        barcode.setBarHeight(28f);
        barcode.setSize(7f);
        barcode.setBaseline(7f);
        barcode.setTextAlignment(Element.ALIGN_CENTER);
        Image barcodeImage = barcode.createImageWithBarcode(cb, null, null);

        PdfPCell cell = new PdfPCell();
        cell.setPadding(4);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.addElement(new Paragraph(label, labelFont));
        com.lowagie.text.Chunk imageChunk = new com.lowagie.text.Chunk(barcodeImage, 0, 0);
        Paragraph imgParagraph = new Paragraph();
        imgParagraph.add(imageChunk);
        imgParagraph.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(imgParagraph);
        return cell;
    }

    private void addIdCell(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(4);
        cell.addElement(new Paragraph(label, labelFont));
        cell.addElement(new Paragraph(value, valueFont));
        table.addCell(cell);
    }

    private String safe(String s) {
        return s != null ? s : "-";
    }
}
