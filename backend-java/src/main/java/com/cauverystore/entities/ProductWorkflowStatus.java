package com.cauverystore.entities;

/**
 * Where a product listing is in the compliance approval workflow.
 *
 * DRAFT -> COMPLIANCE_CHECK -> SELLER_REVIEW -> ADMIN_REVIEW -> APPROVED -> PUBLISHED.
 * COMPLIANCE_CHECK is passed through automatically: the engine runs, and the listing moves
 * on to SELLER_REVIEW if it may be published, or back to DRAFT with the gaps listed.
 */
public enum ProductWorkflowStatus {
    DRAFT, COMPLIANCE_CHECK, SELLER_REVIEW, ADMIN_REVIEW, APPROVED, PUBLISHED;

    public static ProductWorkflowStatus from(String value) {
        if (value == null || value.isBlank()) return DRAFT;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DRAFT;
        }
    }
}
