package com.cauverystore.service;

import com.cauverystore.entities.Category;
import com.cauverystore.repository.CategoryRepository;
import com.cauverystore.repository.ProductRepository;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class StandardCategoriesTest {

    @Test
    void everyEntry_shouldPassTheSameChecksAsTheCategoryForm() {
        Set<String> names = new HashSet<>();
        for (StandardCategories.Entry e : StandardCategories.ALL) {
            assertTrue(names.add(e.name().toLowerCase(Locale.ROOT)), "duplicate: " + e.name());
            int len = e.description().trim().length();
            assertTrue(len >= 10 && len <= 500, e.name() + " description must be 10-500 characters");
            if (e.requiredLicence() != null) {
                assertTrue(SellerLicenceService.TYPES.stream().anyMatch(t -> t.code().equals(e.requiredLicence())),
                        e.name() + " uses an unknown licence code " + e.requiredLicence());
            }
        }
        assertTrue(names.contains("others"), "bulk uploads rely on an Others category");
    }

    @Test
    void addStandardCategories_shouldOnlyAddTheMissingOnesAndLeaveExistingAlone() {
        CategoryRepository repo = mock(CategoryRepository.class);
        Category existing = new Category();
        existing.setName("home & kitchen"); // different capitals still counts as present
        existing.setDescription("The owner's own wording");
        when(repo.findAll()).thenReturn(List.of(existing));
        when(repo.findByName(anyString())).thenReturn(Optional.empty());
        when(repo.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));
        CategoryServiceImpl service = new CategoryServiceImpl(repo, mock(ProductRepository.class),
                mock(AuditService.class), mock(AuthorizationService.class));

        List<String> added = service.addStandardCategories();

        assertEquals(StandardCategories.ALL.size() - 1, added.size());
        assertFalse(added.contains("Home & Kitchen"));
        assertEquals("The owner's own wording", existing.getDescription());
        assertTrue(added.contains("Toys & Games"));
    }

    @Test
    void addStandardCategories_shouldSetTheLicenceSellersNeed() {
        CategoryRepository repo = mock(CategoryRepository.class);
        when(repo.findAll()).thenReturn(List.of());
        when(repo.findByName(anyString())).thenReturn(Optional.empty());
        List<Category> saved = new java.util.ArrayList<>();
        when(repo.save(any(Category.class))).thenAnswer(i -> { saved.add(i.getArgument(0)); return i.getArgument(0); });
        CategoryServiceImpl service = new CategoryServiceImpl(repo, mock(ProductRepository.class),
                mock(AuditService.class), mock(AuthorizationService.class));

        service.addStandardCategories();

        Category grocery = saved.stream().filter(c -> c.getName().equals("Grocery & Staples")).findFirst().orElseThrow();
        assertEquals("FSSAI", grocery.getRequiredLicence());
        Category toys = saved.stream().filter(c -> c.getName().equals("Toys & Games")).findFirst().orElseThrow();
        assertEquals("BIS", toys.getRequiredLicence());
        Category others = saved.stream().filter(c -> c.getName().equals("Others")).findFirst().orElseThrow();
        assertNull(others.getRequiredLicence());
    }
}
