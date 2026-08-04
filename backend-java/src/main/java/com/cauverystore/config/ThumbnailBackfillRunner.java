package com.cauverystore.config;

import com.cauverystore.service.ProductService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ThumbnailBackfillRunner implements CommandLineRunner {
    private final ProductService productService;

    public ThumbnailBackfillRunner(ProductService productService) {
        this.productService = productService;
    }

    @Override
    public void run(String... args) {
        try {
            productService.backfillThumbnails();
        } catch (Exception ignored) {}
    }
}
