package com.cauverystore.service;

import com.cauverystore.entities.SellerLicence;
import com.cauverystore.repository.SellerLicenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

/** Sellers' licence numbers: entered by the seller, checked by an admin, shown to shoppers. */
@Service
public class SellerLicenceService {

    /** One kind of licence: what it is called, when it is needed, and what its number looks like. */
    public record LicenceType(String code, String label, String whenNeeded, Pattern format, String formatHint) {}

    private static final Pattern GENERAL = Pattern.compile("[A-Za-z0-9][A-Za-z0-9 /.\\-]{2,59}");

    public static final List<LicenceType> TYPES = List.of(
            new LicenceType("FSSAI", "FSSAI Licence / Registration",
                    "Needed to sell food, drinks, spices, tea, sweets or health supplements.",
                    Pattern.compile("\\d{14}"), "14 digits"),
            new LicenceType("DRUG", "Drug Licence",
                    "Needed to sell medicines and some medicated cosmetics.", GENERAL, null),
            new LicenceType("BIS", "BIS Certificate (ISI mark)",
                    "Needed for goods under a BIS quality order, such as some electrical goods, toys and helmets.", GENERAL, null),
            new LicenceType("LEGAL_METROLOGY", "Legal Metrology Registration",
                    "Needed by packers and importers of pre-packed goods.", GENERAL, null),
            new LicenceType("IEC", "Import Export Code (IEC)",
                    "Needed if you import the goods you sell.",
                    Pattern.compile("[A-Za-z0-9]{10}"), "10 letters and digits"),
            new LicenceType("TRADE", "Trade Licence",
                    "Issued by your municipality or panchayat for your shop or warehouse.", GENERAL, null),
            new LicenceType("OTHER", "Other licence", "Any other licence your products need.", GENERAL, null));

    private static final Map<String, LicenceType> BY_CODE = new LinkedHashMap<>();
    static {
        for (LicenceType t : TYPES) BY_CODE.put(t.code(), t);
    }

    private final SellerLicenceRepository repo;
    private final AuditService auditService;

    public SellerLicenceService(SellerLicenceRepository repo, AuditService auditService) {
        this.repo = repo;
        this.auditService = auditService;
    }

    public List<Map<String, Object>> types() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (LicenceType t : TYPES) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", t.code());
            m.put("label", t.label());
            m.put("whenNeeded", t.whenNeeded());
            if (t.formatHint() != null) m.put("formatHint", t.formatHint());
            out.add(m);
        }
        return out;
    }

    public List<Map<String, Object>> list(Long sellerId) {
        return repo.findBySellerIdOrderByIdAsc(sellerId).stream().map(SellerLicenceService::toMap).toList();
    }

    /** Adds a licence, or changes one; either way it waits for an admin to check it again. */
    @Transactional
    public Map<String, Object> save(Long sellerId, Long id, Map<String, Object> body) {
        SellerLicence l;
        if (id == null) {
            l = new SellerLicence();
            l.setSellerId(sellerId);
        } else {
            l = repo.findById(id).filter(x -> x.getSellerId().equals(sellerId))
                    .orElseThrow(() -> new IllegalArgumentException("Licence not found."));
        }

        String code = text(body.get("licenceType"));
        LicenceType type = code == null ? null : BY_CODE.get(code.toUpperCase());
        if (type == null) throw new IllegalArgumentException("Choose the kind of licence.");

        String name = text(body.get("licenceName"));
        if ("OTHER".equals(type.code()) && name == null) throw new IllegalArgumentException("Enter the name of the licence.");
        if (name != null && name.length() > 100) throw new IllegalArgumentException("The licence name is too long.");

        String number = text(body.get("licenceNumber"));
        if (number == null) throw new IllegalArgumentException("Enter the licence number.");
        number = number.replaceAll("\\s{2,}", " ");
        if (type.format() != GENERAL) number = number.replace(" ", "");
        if (!type.format().matcher(number).matches()) {
            throw new IllegalArgumentException(type.formatHint() != null
                    ? "A " + type.label() + " number is " + type.formatHint() + "."
                    : "Enter the licence number as printed on the licence (letters, digits, / . - only).");
        }

        LocalDate validUntil;
        try {
            String v = text(body.get("validUntil"));
            validUntil = v == null ? null : LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("The expiry date must be in the form YYYY-MM-DD.");
        }
        if (validUntil != null && validUntil.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("This licence has expired. Add the renewed licence instead.");
        }

        boolean changed = id == null
                || !type.code().equals(l.getLicenceType())
                || !Objects.equals(name, l.getLicenceName())
                || !number.equals(l.getLicenceNumber())
                || !Objects.equals(validUntil, l.getValidUntil());
        l.setLicenceType(type.code());
        l.setLicenceName("OTHER".equals(type.code()) ? name : null);
        l.setLicenceNumber(number);
        l.setValidUntil(validUntil);
        if (changed) {
            l.setStatus("PENDING");
            l.setRejectionReason(null);
            l.setVerifiedBy(null);
            l.setVerifiedAt(null);
        }
        l = repo.save(l);
        auditService.log(sellerId, "seller:" + sellerId, id == null ? "LICENCE_ADDED" : "LICENCE_UPDATED",
                "SellerLicence", l.getId(), type.code() + " " + number, null);
        return toMap(l);
    }

    @Transactional
    public void delete(Long sellerId, Long id) {
        SellerLicence l = repo.findById(id).filter(x -> x.getSellerId().equals(sellerId))
                .orElseThrow(() -> new IllegalArgumentException("Licence not found."));
        repo.delete(l);
        auditService.log(sellerId, "seller:" + sellerId, "LICENCE_REMOVED", "SellerLicence", id,
                l.getLicenceType() + " " + l.getLicenceNumber(), null);
    }

    @Transactional
    public Map<String, Object> review(Long adminId, String adminEmail, Long id, boolean approved, String reason) {
        SellerLicence l = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Licence not found."));
        if (!approved && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("Say why the licence is rejected, so the seller can fix it.");
        }
        l.setStatus(approved ? "VERIFIED" : "REJECTED");
        l.setRejectionReason(approved ? null : reason.trim());
        l.setVerifiedBy(adminEmail);
        l.setVerifiedAt(LocalDateTime.now());
        repo.save(l);
        auditService.log(adminId, adminEmail, approved ? "LICENCE_VERIFIED" : "LICENCE_REJECTED", "SellerLicence", id,
                l.getLicenceType() + " " + l.getLicenceNumber() + (approved ? "" : ": " + reason.trim()), null);
        return toMap(l);
    }

    /** What a shopper sees: checked licences that have not run out. */
    public List<Map<String, Object>> publicFor(Long sellerId) {
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> out = new ArrayList<>();
        for (SellerLicence l : repo.findBySellerIdAndStatusOrderByIdAsc(sellerId, "VERIFIED")) {
            if (l.getValidUntil() != null && l.getValidUntil().isBefore(today)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", label(l));
            m.put("number", l.getLicenceNumber());
            out.add(m);
        }
        return out;
    }

    static String label(SellerLicence l) {
        if ("OTHER".equals(l.getLicenceType()) && l.getLicenceName() != null) return l.getLicenceName();
        LicenceType t = BY_CODE.get(l.getLicenceType());
        return t != null ? t.label() : l.getLicenceType();
    }

    static Map<String, Object> toMap(SellerLicence l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.getId());
        m.put("licenceType", l.getLicenceType());
        m.put("licenceName", l.getLicenceName());
        m.put("label", label(l));
        m.put("licenceNumber", l.getLicenceNumber());
        m.put("validUntil", l.getValidUntil());
        m.put("status", l.getStatus());
        m.put("rejectionReason", l.getRejectionReason());
        m.put("verifiedAt", l.getVerifiedAt());
        return m;
    }

    private static String text(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
