package com.cauverystore.service;

import com.cauverystore.entities.Brand;
import com.cauverystore.entities.SellerBrandApplication;
import com.cauverystore.entities.SellerDocument;
import com.cauverystore.repository.BrandRepository;
import com.cauverystore.repository.SellerBrandApplicationRepository;
import com.cauverystore.repository.SellerDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/** Sellers applying to sell brands, and admins reviewing those applications. */
@Service
public class SellerBrandService {

    public static final Map<String, String> RELATIONSHIPS;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("BRAND_OWNER", "I own this brand");
        m.put("MANUFACTURER", "I make products under this brand");
        m.put("AUTHORISED_SELLER", "The brand owner has authorised me to sell it");
        m.put("DISTRIBUTOR", "I am an appointed distributor");
        RELATIONSHIPS = Collections.unmodifiableMap(m);
    }
    private static final List<String> OPEN = List.of("SUBMITTED", "UNDER_REVIEW");

    private final SellerBrandApplicationRepository appRepo;
    private final BrandRepository brandRepo;
    private final SellerDocumentRepository docRepo;
    private final SellerTeamService teamService;
    private final AuditService auditService;

    public SellerBrandService(SellerBrandApplicationRepository appRepo, BrandRepository brandRepo,
                              SellerDocumentRepository docRepo, SellerTeamService teamService, AuditService auditService) {
        this.appRepo = appRepo;
        this.brandRepo = brandRepo;
        this.docRepo = docRepo;
        this.teamService = teamService;
        this.auditService = auditService;
    }

    /** The seller's applications, plus which brands are protected (need approval to list). */
    public Map<String, Object> forSeller(Long sellerId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("applications", appRepo.findBySellerIdOrderByIdDesc(sellerId).stream().map(this::toMap).toList());
        out.put("protectedBrands", brandRepo.findByGatedTrueOrderByName().stream().map(Brand::getName).toList());
        List<Map<String, String>> rel = new ArrayList<>();
        RELATIONSHIPS.forEach((k, v) -> rel.add(Map.of("code", k, "label", v)));
        out.put("relationships", rel);
        return out;
    }

    @Transactional
    public Map<String, Object> apply(Long sellerId, Long actorId, Map<String, Object> body) {
        String name = text(body.get("brandName"));
        if (name == null || name.length() > 120) throw new IllegalArgumentException("Enter the brand name (up to 120 characters).");
        String rel = text(body.get("relationship"));
        if (rel == null || !RELATIONSHIPS.containsKey(rel)) throw new IllegalArgumentException("Choose how you are connected to the brand.");
        if (!appRepo.findBySellerIdAndBrandNameIgnoreCaseAndStatusIn(sellerId, name, List.of("SUBMITTED", "UNDER_REVIEW", "APPROVED")).isEmpty()) {
            throw new IllegalArgumentException("You already have an application for " + name + " that is open or approved.");
        }
        String tm = text(body.get("trademarkNumber"));
        if (tm != null && !tm.matches("[A-Za-z0-9/ -]{3,40}")) throw new IllegalArgumentException("Enter the trademark number as printed (letters, digits, / and - only).");

        Long documentId = asLong(body.get("documentId"));
        if (documentId != null) {
            SellerDocument d = docRepo.findById(documentId).orElse(null);
            // Uploaded by the seller, or by the team member filling this in (uploads go to their own login).
            if (d == null || d.getUser() == null
                    || !(sellerId.equals(d.getUser().getId()) || d.getUser().getId().equals(actorId))) {
                throw new IllegalArgumentException("That document was not found in your uploads.");
            }
        }
        // An applicant who claims to own or be authorised for a brand must back it with something.
        if (documentId == null && tm == null) {
            throw new IllegalArgumentException("Add the trademark number or upload the trademark certificate or authorisation letter.");
        }

        SellerBrandApplication a = new SellerBrandApplication();
        a.setSellerId(sellerId);
        a.setBrandName(name);
        brandRepo.findFirstByNameIgnoreCase(name).ifPresent(b -> a.setBrandId(b.getId()));
        a.setRelationship(rel);
        a.setTrademarkNumber(tm);
        a.setDocumentId(documentId);
        a.setNotes(text(body.get("notes")));
        SellerBrandApplication saved = appRepo.save(a);
        auditService.log(actorId, null, "BRAND_APPLICATION_SUBMITTED", "SellerBrandApplication", saved.getId(),
                "Seller " + sellerId + ": " + name + " as " + rel, null);
        return toMap(saved);
    }

    @Transactional
    public void withdraw(Long sellerId, Long actorId, Long id) {
        SellerBrandApplication a = appRepo.findById(id).filter(x -> x.getSellerId().equals(sellerId))
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
        if (!OPEN.contains(a.getStatus())) throw new IllegalArgumentException("Only an application still being reviewed can be withdrawn.");
        appRepo.delete(a);
        auditService.log(actorId, null, "BRAND_APPLICATION_WITHDRAWN", "SellerBrandApplication", id,
                "Seller " + sellerId + ": " + a.getBrandName(), null);
    }

    public List<Map<String, Object>> forAdmin(boolean openOnly) {
        List<SellerBrandApplication> list = openOnly ? appRepo.findByStatusInOrderByIdAsc(OPEN) : appRepo.findAllByOrderByIdDesc();
        List<Map<String, Object>> out = new ArrayList<>();
        for (SellerBrandApplication a : list) {
            Map<String, Object> m = toMap(a);
            m.put("storeName", teamService.storeName(a.getSellerId()));
            if (a.getDocumentId() != null) {
                docRepo.findById(a.getDocumentId()).ifPresent(d -> {
                    m.put("documentUrl", d.getFileUrl());
                    m.put("documentName", d.getFileOriginalName());
                });
            }
            out.add(m);
        }
        return out;
    }

    /** UNDER_REVIEW, APPROVED or REJECTED. Approving puts the brand in the store's list if new. */
    @Transactional
    public Map<String, Object> review(Long adminId, String adminEmail, Long id, String action, String reason) {
        SellerBrandApplication a = appRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Application not found."));
        String next = action == null ? "" : action.trim().toUpperCase();
        if (!List.of("UNDER_REVIEW", "APPROVED", "REJECTED").contains(next)) throw new IllegalArgumentException("Unknown action.");
        if (!OPEN.contains(a.getStatus()) && !"REJECTED".equals(next)) {
            throw new IllegalArgumentException("This application has already been decided.");
        }
        if ("REJECTED".equals(next) && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("Say why, so the seller can put it right.");
        }
        if ("APPROVED".equals(next)) {
            Brand brand = brandRepo.findFirstByNameIgnoreCase(a.getBrandName()).orElseGet(() -> {
                Brand b = new Brand();
                b.setName(a.getBrandName());
                b.setActive(true);
                return brandRepo.save(b);
            });
            a.setBrandId(brand.getId());
        }
        a.setStatus(next);
        a.setRejectionReason("REJECTED".equals(next) ? reason.trim() : null);
        if (!"UNDER_REVIEW".equals(next)) {
            a.setReviewedBy(adminEmail);
            a.setReviewedAt(LocalDateTime.now());
        }
        appRepo.save(a);
        auditService.log(adminId, adminEmail, "BRAND_APPLICATION_" + next, "SellerBrandApplication", id,
                a.getBrandName() + " for seller " + a.getSellerId() + (reason != null && !reason.isBlank() ? ": " + reason.trim() : ""), null);
        return toMap(a);
    }

    private Map<String, Object> toMap(SellerBrandApplication a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("sellerId", a.getSellerId());
        m.put("brandName", a.getBrandName());
        m.put("brandId", a.getBrandId());
        m.put("relationship", a.getRelationship());
        m.put("relationshipLabel", RELATIONSHIPS.getOrDefault(a.getRelationship(), a.getRelationship()));
        m.put("trademarkNumber", a.getTrademarkNumber());
        m.put("documentId", a.getDocumentId());
        m.put("notes", a.getNotes());
        m.put("status", a.getStatus());
        m.put("rejectionReason", a.getRejectionReason());
        m.put("reviewedAt", a.getReviewedAt());
        m.put("createdAt", a.getCreatedAt());
        return m;
    }

    private static String text(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Long asLong(Object o) {
        try { return o == null || o.toString().isBlank() ? null : Long.valueOf(o.toString()); } catch (NumberFormatException e) { return null; }
    }
}
