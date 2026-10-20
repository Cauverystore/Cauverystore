package com.cauverystore.service;

import com.cauverystore.entities.SellerDocument;
import com.cauverystore.entities.SellerDocumentFile;
import com.cauverystore.repository.SellerDocumentFileRepository;
import com.cauverystore.repository.SellerDocumentRepository;
import com.cauverystore.util.KycCrypto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Private storage for sellers' verification documents.
 *
 * Files are checked by their contents (not their name), encrypted, and kept in the database.
 * The document record points at an internal, sign-in-only address instead of a public link.
 */
@Service
public class KycDocumentService {

    public static final String FILE_PATH = "/api/seller-registration/document-file/";
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final SellerRegistrationService registrationService;
    private final SellerDocumentRepository docRepo;
    private final SellerDocumentFileRepository fileRepo;
    private final KycCrypto crypto;
    private final AuditService auditService;

    public KycDocumentService(SellerRegistrationService registrationService, SellerDocumentRepository docRepo,
                              SellerDocumentFileRepository fileRepo, KycCrypto crypto, AuditService auditService) {
        this.registrationService = registrationService;
        this.docRepo = docRepo;
        this.fileRepo = fileRepo;
        this.crypto = crypto;
        this.auditService = auditService;
    }

    /** A file a person may be shown: the bytes and how to label them. */
    public record StoredFile(byte[] bytes, String contentType, String fileName) {}

    @Transactional
    public Map<String, Object> upload(Long userId, MultipartFile file, String documentType, String documentName, LocalDate expiryDate) {
        if (!crypto.isConfigured()) throw new KycCrypto.NotConfiguredException();
        if (documentType == null || !documentType.matches("[A-Z_]{2,40}")) {
            throw new IllegalArgumentException("Unknown document type.");
        }
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Choose a file to upload.");
        if (file.getSize() > MAX_BYTES) throw new IllegalArgumentException("The file is too large. The limit is 5 MB.");

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new IllegalArgumentException("The file could not be read. Please try again.");
        }
        String contentType = detectType(bytes);
        if (contentType == null) {
            throw new IllegalArgumentException("Upload a PDF, JPG or PNG file.");
        }

        String originalName = safeName(file.getOriginalFilename());
        Map<String, Object> created = registrationService.uploadDocument(userId, documentType,
                documentName != null && !documentName.isBlank() ? documentName : documentType,
                FILE_PATH + "pending", originalName, (long) bytes.length, contentType, expiryDate);
        SellerDocument doc = (SellerDocument) created.get("document");

        SellerDocumentFile f = new SellerDocumentFile();
        f.setDocumentId(doc.getId());
        f.setUserId(userId);
        f.setContentType(contentType);
        f.setFileName(originalName);
        f.setFileSize((long) bytes.length);
        f.setSha256(sha256(bytes));
        f.setData(crypto.encryptBytes(bytes));
        f = fileRepo.save(f);

        doc.setFileUrl(FILE_PATH + f.getId());
        docRepo.save(doc);

        Map<String, Object> out = new LinkedHashMap<>(created);
        out.put("document", doc);
        return out;
    }

    /**
     * Reads a file back for the seller who uploaded it, or for an admin. Anyone else is told it
     * does not exist, so the address cannot be used to find out which files are there.
     */
    @Transactional
    public StoredFile read(Long fileId, Long currentUserId, String currentUserEmail, boolean isAdmin) {
        SellerDocumentFile f = fileRepo.findById(fileId).orElse(null);
        if (f == null || (!isAdmin && !f.getUserId().equals(currentUserId))) {
            throw new IllegalArgumentException("File not found.");
        }
        byte[] bytes = crypto.decryptBytes(f.getData());
        if (isAdmin && !f.getUserId().equals(currentUserId)) {
            auditService.log(currentUserId, currentUserEmail, "KYC_DOCUMENT_VIEWED", "SellerDocumentFile", f.getId(),
                    "Viewed document " + f.getDocumentId() + " of seller " + f.getUserId(), null);
        }
        return new StoredFile(bytes, f.getContentType(), f.getFileName());
    }

    /** By the first bytes, so a renamed file of another kind is turned away. */
    static String detectType(byte[] b) {
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') return "application/pdf";
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        return null;
    }

    static String safeName(String name) {
        if (name == null || name.isBlank()) return "document";
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("[^A-Za-z0-9._ -]", "_");
        return base.length() > 120 ? base.substring(base.length() - 120) : base;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
