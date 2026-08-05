package com.cauverystore.controller;

import com.cauverystore.dto.AuthResponse;
import com.cauverystore.dto.LoginRequest;
import com.cauverystore.entities.Category;
import com.cauverystore.entities.Role;
import com.cauverystore.entities.User;
import com.cauverystore.repository.CategoryRepository;
import com.cauverystore.service.AuthService;
import com.cauverystore.util.CookieUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    private final AuthService authService;
    private final CategoryRepository categoryRepo;
    private final CookieUtil cookieUtil;

    public AdminAuthController(AuthService authService, CategoryRepository categoryRepo, CookieUtil cookieUtil) {
        this.authService = authService;
        this.categoryRepo = categoryRepo;
        this.cookieUtil = cookieUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        AuthResponse authResponse = authService.authenticate(request);
        User user = authService.getUserByEmail(authResponse.getEmail());
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.SUPER_ADMIN) {
            throw new RuntimeException("Access denied. Admin credentials required.");
        }
        if (authResponse.getRefreshToken() != null) {
            cookieUtil.setRefreshTokenCookie(response, authResponse.getRefreshToken());
            authResponse.setRefreshToken(null);
        }
        return ResponseEntity.ok(authResponse);
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'EXECUTIVE')")
    public ResponseEntity<List<Category>> getCategories() {
        return ResponseEntity.ok(categoryRepo.findAll());
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Category> createCategory(@RequestBody Map<String, String> body) {
        Category cat = new Category();
        cat.setName(body.get("name"));
        cat.setDescription(body.getOrDefault("description", ""));
        return ResponseEntity.ok(categoryRepo.save(cat));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Category> updateCategory(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Category cat = categoryRepo.findById(id).orElseThrow();
        if (body.containsKey("name")) cat.setName(body.get("name"));
        if (body.containsKey("description")) cat.setDescription(body.get("description"));
        return ResponseEntity.ok(categoryRepo.save(cat));
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> deleteCategory(@PathVariable Long id) {
        categoryRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }
}
