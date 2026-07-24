package com.cauverystore.controller;

import com.cauverystore.repository.ProductQuestionRepository;
import com.cauverystore.repository.ProductAnswerRepository;
import com.cauverystore.repository.ProductRepository;
import com.cauverystore.repository.UserRepository;
import com.cauverystore.entities.ProductQuestion;
import com.cauverystore.entities.ProductAnswer;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
public class ProductQnaController {

    private final ProductQuestionRepository qRepo;
    private final ProductAnswerRepository aRepo;
    private final ProductRepository pRepo;
    private final UserRepository uRepo;

    public ProductQnaController(ProductQuestionRepository qr, ProductAnswerRepository ar, ProductRepository pr, UserRepository ur) {
        this.qRepo = qr; this.aRepo = ar; this.pRepo = pr; this.uRepo = ur;
    }

    @GetMapping("/api/products/{productId}/questions")
    public ResponseEntity<?> getQuestions(@PathVariable Long productId) {
        List<Map<String,Object>> list = qRepo.findByProduct_Id(productId).stream().map(q->{
            Map<String,Object> m=new LinkedHashMap<>(); m.put("id",q.getId()); m.put("question",q.getQuestion());
            m.put("userName",q.getUser()!=null?q.getUser().getFullName():""); m.put("createdAt",q.getCreatedAt()!=null?q.getCreatedAt().toString():null);
            m.put("answers",new ArrayList<>()); m.put("answered",false); return m;
        }).toList();
        return ResponseEntity.ok(list);
    }

    @PostMapping("/api/products/{productId}/questions")
    public ResponseEntity<?> askQuestion(@PathVariable Long productId, @RequestBody Map<String,String> body) {
        var product = pRepo.findById(productId).orElse(null);
        if(product==null) return ResponseEntity.badRequest().body(Map.of("error","Product not found"));
        var user = uRepo.findByUsername(SecurityContextHolder.getContext().getAuthentication().getName());
        ProductQuestion q = new ProductQuestion(); q.setQuestion(body.get("question")); q.setProduct(product); q.setUser(user);
        qRepo.save(q);
        return ResponseEntity.ok(Map.of("id",q.getId(),"question",q.getQuestion()));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    @PostMapping("/api/admin/questions/{questionId}/answers")
    public ResponseEntity<?> answerQuestion(@PathVariable Long questionId, @RequestBody Map<String,String> body) {
        var q = qRepo.findById(questionId).orElse(null);
        if(q==null) return ResponseEntity.badRequest().body(Map.of("error","Not found"));
        var user = uRepo.findByUsername(SecurityContextHolder.getContext().getAuthentication().getName());
        ProductAnswer a = new ProductAnswer(); a.setAnswer(body.get("answer")); a.setQuestion(q); a.setUser(user);
        aRepo.save(a);
        return ResponseEntity.ok(Map.of("id",a.getId(),"answer",a.getAnswer()));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    @DeleteMapping("/api/admin/questions/{id}")
    public ResponseEntity<?> deleteQ(@PathVariable Long id) { qRepo.deleteById(id); return ResponseEntity.ok(Map.of("message","Deleted")); }

    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    @DeleteMapping("/api/admin/answers/{id}")
    public ResponseEntity<?> deleteA(@PathVariable Long id) { aRepo.deleteById(id); return ResponseEntity.ok(Map.of("message","Deleted")); }

    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'EXECUTIVE', 'SUPER_ADMIN')")
    @GetMapping("/api/admin/questions")
    public ResponseEntity<?> allQuestions() { return ResponseEntity.ok(qRepo.findAll()); }
}
