package com.cauverystore.controller;

import com.cauverystore.entities.Order;
import com.cauverystore.dto.AdminDashboardResponse;
import com.cauverystore.service.OrderService;
import com.cauverystore.service.ShippingLabelService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'EXECUTIVE', 'SUPER_ADMIN')")
public class AdminOrderController {

    private final OrderService orderService;
    private final ShippingLabelService shippingLabelService;

    @GetMapping
    public ResponseEntity<Page<Order>> getAllOrders(Pageable pageable, @RequestParam(required = false) String status) {
        return ResponseEntity.ok(orderService.getAllOrders(pageable, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Order> updateStatus(@PathVariable Long id,
                                                              @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, body.get("status")));
    }

    @PutMapping("/{id}/ship")
    public ResponseEntity<Order> markShipped(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.markShipped(id));
    }

    @PutMapping("/{id}/deliver")
    public ResponseEntity<Order> markDelivered(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.markDelivered(id));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<Order> cancelOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.adminCancelOrder(id));
    }

    @PutMapping("/{id}/assign-courier")
    public ResponseEntity<Order> assignCourier(@PathVariable Long id,
                                                @RequestBody Map<String, String> body) {
        String courier = body.get("courier");
        String trackingNumber = body.get("trackingNumber");
        return ResponseEntity.ok(orderService.assignCourier(id, courier, trackingNumber));
    }

    @PutMapping("/{id}/refund")
    public ResponseEntity<Order> refundOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.refundOrder(id));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardResponse> getDashboard() {
        return ResponseEntity.ok(orderService.getAdminDashboard());
    }

    @GetMapping("/{id}/shipping-label/pdf")
    public ResponseEntity<?> downloadShippingLabel(@PathVariable Long id) {
        try {
            byte[] pdf = shippingLabelService.generateLabelPdf(id);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "shipping-label-" + id + ".pdf");
            return ResponseEntity.ok().headers(headers).body(pdf);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
