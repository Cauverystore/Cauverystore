package com.cauverystore.controller;

import com.cauverystore.entities.Product;
import com.cauverystore.entities.ProductCompliance;
import com.cauverystore.service.GstRateResolver;
import com.cauverystore.service.ProductComplianceService;
import com.cauverystore.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the shop's product page shows a customer about the goods: name, brand, SKU, HSN, GST rate,
 * MRP, price, net quantity, country of origin, manufacturer, customer care, warranty, and the
 * return and refund policy.
 *
 * Public, like the other GET /api/products reads, and only for a product shoppers can see.
 * Nothing about the seller's tax registration is included here.
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductLabelInfoController {

    private final ProductService productService;
    private final ProductComplianceService compliance;
    private final GstRateResolver rateResolver;

    @GetMapping("/{id}/label-info")
    public ResponseEntity<Map<String, Object>> labelInfo(@PathVariable Long id) {
        Product p = productService.getProductForShoppers(id);
        ProductCompliance c = compliance.findRecord(id).orElse(new ProductCompliance());
        Double selling = p.getOfferPrice() != null && p.getOfferPrice() > 0 ? p.getOfferPrice() : p.getPrice();

        Double gstRate = null;
        try {
            gstRate = rateResolver.resolve(p, false, LocalDate.now(), selling).getTotalRate();
        } catch (RuntimeException e) {
            // No published rate: the field is left out rather than guessed.
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.getName());
        m.put("brand", p.getBrand());
        m.put("sku", p.getSku());
        m.put("hsnCode", p.getHsnCode());
        m.put("gstRate", gstRate);
        m.put("mrp", p.getMrp());
        m.put("price", selling);
        m.put("netQuantity", c.getNetQuantity());
        m.put("quantityUnit", c.getQuantityUnit());
        m.put("countryOfOrigin", p.getCountryOfOrigin());
        m.put("manufacturerName", has(c.getManufacturerName()) ? c.getManufacturerName() : p.getManufacturer());
        m.put("manufacturerAddress", c.getManufacturerAddress());
        Map<String, Object> care = new LinkedHashMap<>();
        care.put("name", c.getCustomerCareName());
        care.put("phone", c.getCustomerCarePhone());
        care.put("email", c.getCustomerCareEmail());
        care.put("address", c.getCustomerCareAddress());
        m.put("customerCare", care);
        m.put("warranty", p.getWarranty());
        m.put("returnPolicy", p.getReturnPolicy());
        m.put("returnEligible", p.getReturnEligible());
        m.put("returnWindow", p.getReturnWindow());
        return ResponseEntity.ok(m);
    }

    private static boolean has(String s) { return s != null && !s.isBlank(); }
}
