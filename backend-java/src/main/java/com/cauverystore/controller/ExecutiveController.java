package com.cauverystore.controller;

import com.cauverystore.service.ExecutiveService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/executive")
@PreAuthorize("hasAnyRole('EXECUTIVE', 'SUPER_ADMIN')")
public class ExecutiveController {

    private final ExecutiveService executiveService;

    public ExecutiveController(ExecutiveService executiveService) {
        this.executiveService = executiveService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard() {
        return ResponseEntity.ok(executiveService.getDashboardSummary());
    }

    @GetMapping("/sales/overview")
    public ResponseEntity<?> getSalesOverview() {
        return ResponseEntity.ok(executiveService.getSalesOverview());
    }

    @GetMapping("/sales/top-products")
    public ResponseEntity<?> getTopSellingProducts(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(executiveService.getTopSellingProducts(limit));
    }

    @GetMapping("/sales/seller-breakdown")
    public ResponseEntity<?> getSellerSalesBreakdown() {
        return ResponseEntity.ok(executiveService.getSellerSalesBreakdown());
    }

    @GetMapping("/orders/in-progress")
    public ResponseEntity<?> getOrdersInProgress() {
        return ResponseEntity.ok(executiveService.getOrdersInProgress());
    }

    @GetMapping("/orders/completed")
    public ResponseEntity<?> getCompletedOrders() {
        return ResponseEntity.ok(executiveService.getCompletedOrders());
    }

    @GetMapping("/orders/returns-refunds")
    public ResponseEntity<?> getReturnRefundStats() {
        return ResponseEntity.ok(executiveService.getReturnRefundStats());
    }

    @GetMapping("/orders/pending-approvals")
    public ResponseEntity<?> getPendingApprovals() {
        return ResponseEntity.ok(executiveService.getPendingApprovals());
    }

    @GetMapping("/inventory/insights")
    public ResponseEntity<?> getInventoryInsights() {
        return ResponseEntity.ok(executiveService.getInventoryInsights());
    }

    @GetMapping("/inventory/warehouses")
    public ResponseEntity<?> getWarehouseStockSummary() {
        return ResponseEntity.ok(executiveService.getWarehouseStockSummary());
    }

    @GetMapping("/sellers/performance")
    public ResponseEntity<?> getSellerPerformance() {
        return ResponseEntity.ok(executiveService.getSellerPerformance());
    }

    @GetMapping("/customers/activity")
    public ResponseEntity<?> getCustomerActivitySummary() {
        return ResponseEntity.ok(executiveService.getCustomerActivitySummary());
    }

    @GetMapping("/customers/reviews/sentiment")
    public ResponseEntity<?> getReviewSentimentAnalysis() {
        return ResponseEntity.ok(executiveService.getReviewSentimentAnalysis());
    }

    @GetMapping("/notifications")
    public ResponseEntity<?> getNotifications() {
        return ResponseEntity.ok(executiveService.getNotifications());
    }

    @GetMapping("/reports/generate")
    public ResponseEntity<?> generateReport(@RequestParam String type, @RequestParam(required = false) String filter) {
        return ResponseEntity.ok(executiveService.generateReport(type, filter));
    }
}
