package com.cauverystore.controller;

import com.cauverystore.entities.ReturnRequest;
import com.cauverystore.service.ReturnRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/returns")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'EXECUTIVE')")
public class ReturnRequestController {
    private final ReturnRequestService returnService;
    public ReturnRequestController(ReturnRequestService returnService) { this.returnService = returnService; }

    @GetMapping public List<ReturnRequest> getAll() { return returnService.getAll(); }
    @GetMapping("/{id}") public ReturnRequest getById(@PathVariable Long id) { return returnService.getById(id); }
    @PutMapping("/{id}/status") public ReturnRequest updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) { return returnService.updateStatus(id, body.get("status")); }
    @GetMapping("/status/{status}") public List<ReturnRequest> getByStatus(@PathVariable String status) { return returnService.getByStatus(status); }
}
