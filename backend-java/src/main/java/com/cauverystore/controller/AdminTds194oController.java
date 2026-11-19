package com.cauverystore.controller;

import com.cauverystore.service.Tds194oService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Read-only: how much 194-O TDS should be held back from each seller in a financial year. */
@RestController
@RequestMapping("/api/admin/tds-194o")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminTds194oController {

    private final Tds194oService service;

    public AdminTds194oController(Tds194oService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<?> report(@RequestParam("fy") String fy) {
        try {
            return ResponseEntity.ok(service.report(fy));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
