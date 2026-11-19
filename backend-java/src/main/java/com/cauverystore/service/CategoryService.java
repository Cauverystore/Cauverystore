package com.cauverystore.service;

import com.cauverystore.entities.Category;

import java.util.List;

public interface CategoryService {
    List<Category> getAllCategories();
    List<Category> getCategoriesWithProducts();
    Category getCategory(Long id);
    Category addCategory(Category category, String authHeader);
    Category updateCategory(Long id, Category category, String authHeader);
    void deleteCategory(Long id, String authHeader);
    /** Adds the StandardCategories the store does not have yet; returns the names added. */
    List<String> addStandardCategories();
}
