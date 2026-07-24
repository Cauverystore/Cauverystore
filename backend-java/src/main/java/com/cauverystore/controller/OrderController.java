package com.cauverystore.controller;

import com.cauverystore.dto.InvoiceResponse;
import com.cauverystore.dto.OrderTimelineResponse;
import com.cauverystore.entities.Order;
import com.cauverystore.entities.OrderItem;
import com.cauverystore.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin("*")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/place")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Map<String, Object>> placeOrder(@RequestBody Map<String, Object> body,
                                                           @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(orderService.placeOrder(authHeader, body));
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<Map<String, Object>>> getOrders(@RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(orderService.getOrdersByHeader(authHeader));
    }

    @GetMapping("/{orderId}/items")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<OrderItem>> getOrderItems(@PathVariable Long orderId,
                                                           @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(orderService.getOrderItems(orderId, authHeader));
    }

    @GetMapping("/{orderId}/invoice")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InvoiceResponse> getInvoice(@PathVariable Long orderId,
                                                        @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(orderService.getInvoice(orderId, authHeader));
    }

    @GetMapping("/{orderId}/invoice/pdf")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> downloadInvoicePdf(@PathVariable Long orderId,
                                                         @RequestHeader("Authorization") String authHeader) {
        Resource resource = orderService.generateInvoicePdf(orderId, authHeader);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=invoice-" + orderId + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }

    @GetMapping("/{orderId}/timeline")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderTimelineResponse> getTimeline(@PathVariable Long orderId,
                                                               @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(orderService.getOrderTimeline(orderId, authHeader));
    }

    @PutMapping("/{orderId}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Map<String, Object>> cancelOrder(@PathVariable Long orderId,
                                                            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(orderService.cancelOrder(authHeader, orderId));
    }

    @PutMapping("/{orderId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Order> updateOrderStatus(@PathVariable Long orderId,
                                                                  @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(orderService.updateOrderStatus(orderId, body.get("status")));
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }
}
