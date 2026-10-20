package com.cauverystore.entities;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * The file behind a seller's KYC document (PAN, Aadhaar, GST certificate and so on).
 *
 * Kept in the database, encrypted with KYC_ENCRYPTION_KEY, rather than on a public image host:
 * these are identity documents, and a link anyone can open is a leak. Only the seller who
 * uploaded it and admins can read it back, through an authenticated endpoint.
 *
 * Its own table so that listing a seller's documents never loads the files themselves. Each
 * upload is a new row, so an earlier version is still there after a re-upload.
 */
@Entity
@Table(name = "seller_document_files", indexes = {
        @Index(name = "idx_seller_document_files_doc", columnList = "document_id")
})
public class SellerDocumentFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "content_type", length = 50, nullable = false)
    private String contentType;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /** SHA-256 of the original file, to spot a duplicate upload and to prove what was received. */
    @Column(name = "sha256", length = 64, nullable = false)
    private String sha256;

    /** 12-byte IV followed by the AES-GCM ciphertext. */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "data", nullable = false, columnDefinition = "bytea")
    private byte[] data;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
