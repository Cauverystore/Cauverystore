package com.cauverystore.controller;

import com.cauverystore.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasAnyRole('ADMIN', 'EXECUTIVE', 'SUPER_ADMIN')")
@CrossOrigin("*")
public class AdminReportController {
    private final ReportService reportService;
    public AdminReportController(ReportService reportService) { this.reportService = reportService; }

    @GetMapping("/sales")
    public ResponseEntity<Map<String, Object>> getSalesReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.getSalesReport(from, to));
    }

    @GetMapping("/products")
    public ResponseEntity<?> getProductReport() {
        return ResponseEntity.ok(reportService.getProductReport());
    }
}
