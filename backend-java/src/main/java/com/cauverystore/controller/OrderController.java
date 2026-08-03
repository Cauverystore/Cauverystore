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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    public ResponseEntity<List<Map<String, Object>>> getOrders(@RequestHeader("Authorization") String authHeader,
                                                                 @RequestParam(required = false) String status) {
        return ResponseEntity.ok(orderService.getOrdersByHeader(authHeader, status));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getOrderDetail(@PathVariable Long orderId,
                                                                @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(orderService.getOrderDetail(orderId, authHeader));
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

    @PostMapping("/{orderId}/return")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Map<String, Object>> requestReturn(@PathVariable Long orderId,
                                                               @RequestBody Map<String, String> body,
                                                               @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(orderService.createReturnRequest(authHeader, orderId, body.get("reason")));
    }

    @PutMapping("/{orderId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Order> updateOrderStatus(@PathVariable Long orderId,
                                                                  @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(orderService.updateOrderStatus(orderId, body.get("status")));
    }

    @PostMapping("/bulk-cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<Map<String, Object>>> bulkCancelOrders(
            @RequestBody Map<String, List<Long>> body,
            @RequestHeader("Authorization") String authHeader) {
        List<Long> ids = body.getOrDefault("ids", List.of());
        List<Map<String, Object>> results = new ArrayList<>();
        for (Long orderId : ids) {
            try {
                Map<String, Object> result = orderService.cancelOrder(authHeader, orderId);
                result.put("orderId", orderId);
                result.put("success", true);
                results.add(result);
            } catch (Exception e) {
                Map<String, Object> err = new HashMap<>();
                err.put("orderId", orderId);
                err.put("success", false);
                err.put("error", e.getMessage());
                results.add(err);
            }
        }
        return ResponseEntity.ok(results);
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }
}
