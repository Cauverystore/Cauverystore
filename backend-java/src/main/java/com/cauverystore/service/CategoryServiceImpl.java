package com.cauverystore.service;

import com.cauverystore.entities.Category;
import com.cauverystore.repository.CategoryRepository;
import com.cauverystore.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepo;
    private final ProductRepository productRepo;
    private final AuditService auditService;
    private final AuthorizationService authorizationService;

    public CategoryServiceImpl(CategoryRepository categoryRepo,
                               ProductRepository productRepo,
                               AuditService auditService,
                               AuthorizationService authorizationService) {
        this.categoryRepo = categoryRepo;
        this.productRepo = productRepo;
        this.auditService = auditService;
        this.authorizationService = authorizationService;
    }

    @Override
    public List<Category> getAllCategories() {
        return categoryRepo.findAll();
    }

    @Override
    public List<Category> getCategoriesWithProducts() {
        return productRepo.findCategoriesWithVisibleProducts();
    }

    @Override
    public Category getCategory(Long id) {
        return categoryRepo.findById(id).orElse(null);
    }

    @Override
    public Category addCategory(Category category, String authHeader) {
        if (category.getName() == null || category.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Category name is required");
        }
        String name = category.getName().trim();
        Optional<Category> existing = categoryRepo.findByName(name);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Category already exists");
        }
        String desc = category.getDescription();
        if (desc != null && !desc.trim().isEmpty()) {
            if (desc.trim().length() < 10) {
                throw new IllegalArgumentException("Description must be at least 10 characters");
            }
            if (desc.trim().length() > 500) {
                throw new IllegalArgumentException("Description must not exceed 500 characters");
            }
            category.setDescription(desc.trim());
        }
        category.setName(name);
        category.setRequiredLicence(checkedLicence(category.getRequiredLicence()));
        Category saved = categoryRepo.save(category);
        String email = authorizationService.getCurrentUserEmail();
        auditService.log(null, email, "CATEGORY_CREATED", "Category",
                saved.getId(), "Created category: " + saved.getName(), null);
        return saved;
    }

    @Override
    public Category updateCategory(Long id, Category updated, String authHeader) {
        Category cat = categoryRepo.findById(id).orElse(null);
        if (cat == null) return null;

        if (updated.getName() == null || updated.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Category name is required");
        }
        String newName = updated.getName().trim();
        Optional<Category> existing = categoryRepo.findByName(newName);
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            throw new IllegalArgumentException("Category already exists");
        }

        String desc = updated.getDescription();
        if (desc != null && !desc.trim().isEmpty()) {
            if (desc.trim().length() < 10) {
                throw new IllegalArgumentException("Description must be at least 10 characters");
            }
            if (desc.trim().length() > 500) {
                throw new IllegalArgumentException("Description must not exceed 500 characters");
            }
            cat.setDescription(desc.trim());
        } else {
            cat.setDescription(null);
        }
        cat.setName(newName);
        String before = cat.getRequiredLicence();
        cat.setRequiredLicence(checkedLicence(updated.getRequiredLicence()));

        Category saved = categoryRepo.save(cat);
        if (!java.util.Objects.equals(before, saved.getRequiredLicence())) {
            auditService.log(null, authorizationService.getCurrentUserEmail(), "CATEGORY_LICENCE_CHANGED", "Category",
                    saved.getId(), saved.getName() + ": " + before + " -> " + saved.getRequiredLicence(), null);
        }
        String email = authorizationService.getCurrentUserEmail();
        auditService.log(null, email, "CATEGORY_UPDATED", "Category",
                saved.getId(), "Updated category: " + saved.getName(), null);
        return saved;
    }

    @Override
    public void deleteCategory(Long id, String authHeader) {
        Category cat = categoryRepo.findById(id).orElse(null);
        if (cat == null) return;

        long productCount = productRepo.countByCategory(cat);
        if (productCount > 0) {
            throw new IllegalStateException(
                    "Cannot delete category '" + cat.getName() + "' — " + productCount +
                    " product(s) are still linked to it. Reassign products first.");
        }

        String email = authorizationService.getCurrentUserEmail();
        auditService.log(null, email, "CATEGORY_DELETED", "Category",
                id, "Deleted category: " + cat.getName(), null);
        categoryRepo.deleteById(id);
    }

    @Override
    public List<String> addStandardCategories() {
        // Compare names ignoring capitals and spaces, so "home & kitchen" counts as present.
        java.util.Set<String> have = new java.util.HashSet<>();
        for (Category c : categoryRepo.findAll()) {
            if (c.getName() != null) have.add(c.getName().trim().toLowerCase(java.util.Locale.ROOT));
        }
        List<String> added = new java.util.ArrayList<>();
        for (StandardCategories.Entry e : StandardCategories.ALL) {
            if (have.contains(e.name().toLowerCase(java.util.Locale.ROOT))) continue;
            Category c = new Category();
            c.setName(e.name());
            c.setDescription(e.description());
            c.setRequiredLicence(e.requiredLicence());
            addCategory(c, null);
            added.add(e.name());
        }
        return added;
    }

    /** A licence code a category may require, or null for none. */
    private static String checkedLicence(String code) {
        if (code == null || code.isBlank()) return null;
        String c = code.trim().toUpperCase();
        boolean known = SellerLicenceService.TYPES.stream().anyMatch(t -> t.code().equals(c));
        if (!known) throw new IllegalArgumentException("Unknown licence type: " + code);
        return c;
    }
}
