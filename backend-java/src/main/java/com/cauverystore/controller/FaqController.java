package com.cauverystore.controller;

import com.cauverystore.entities.Faq;
import com.cauverystore.service.FaqService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/faqs")
public class FaqController {
    private final FaqService faqService;
    public FaqController(FaqService faqService) { this.faqService = faqService; }

    @GetMapping
    public ResponseEntity<List<Faq>> getAll() { return ResponseEntity.ok(faqService.getActive()); }
}
