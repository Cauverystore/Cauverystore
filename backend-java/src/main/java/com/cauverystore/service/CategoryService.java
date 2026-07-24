package com.cauverystore.service;

import com.cauverystore.entities.Category;

import java.util.List;

public interface CategoryService {
    List<Category> getAllCategories();
    Category getCategory(Long id);
    Category addCategory(Category category, String authHeader);
    Category updateCategory(Long id, Category category, String authHeader);
    void deleteCategory(Long id, String authHeader);
}
