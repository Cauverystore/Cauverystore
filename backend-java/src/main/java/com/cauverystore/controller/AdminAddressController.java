package com.cauverystore.controller;

import com.cauverystore.service.AddressService;
import com.cauverystore.service.AuditService;
import com.cauverystore.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/addresses")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminAddressController {

    private final AddressService addressService;
    private final AuthService authService;
    private final AuditService auditService;

    /**
     * Every address on the system - active and soft-deleted - with its owner and how many
     * orders reference it.
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllAddresses() {
        return ResponseEntity.ok(addressService.getAllAddressesWithUsage());
    }

    /**
     * Re-activates a soft-deleted address. Keeping the restore admin-only matters: a customer
     * could otherwise resurrect a row they removed - and if they removed it because it was
     * wrong, the wrong address keeps coming back at checkout.
     */
    @PostMapping("/{id}/restore")
    public ResponseEntity<Map<String, Object>> restoreAddress(@PathVariable Long id) {
        addressService.restoreAddress(id);
        Long adminId = authService.deriveUserId();
        String adminEmail = authService.getUserById(adminId).getEmail();
        auditService.log(adminId, adminEmail, "ADDRESS_RESTORED",
                "Address", id, "Address #" + id + " restored by admin", null);
        return ResponseEntity.ok(Map.of("message", "Address restored"));
    }
}