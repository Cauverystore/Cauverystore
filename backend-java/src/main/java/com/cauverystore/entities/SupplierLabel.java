package com.cauverystore.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What a person confirmed from a supplier's pack label or written reply, for one supplier item
 * code. Auto-publishing relies on this rather than on anything the AI wrote.
 */
@Entity
@Table(name = "supplier_labels", uniqueConstraints = @UniqueConstraint(name = "uk_supplier_labels_sku", columnNames = "sku"))
@Getter
@Setter
public class SupplierLabel extends BaseEntity {

    @Column(nullable = false, length = 60)
    private String sku;

    @Column(name = "hsn_code", length = 8)
    private String hsnCode;
    @Column(name = "gst_rate_selection_id")
    private Long gstRateSelectionId;
    private Double mrp;

    @Column(name = "manufacturer_name", length = 200)
    private String manufacturerName;
    @Column(name = "manufacturer_address", length = 500)
    private String manufacturerAddress;
    @Column(name = "packer_name", length = 200)
    private String packerName;
    @Column(name = "packer_address", length = 500)
    private String packerAddress;
    @Column(name = "importer_name", length = 200)
    private String importerName;
    @Column(name = "importer_address", length = 500)
    private String importerAddress;

    @Column(name = "country_of_origin", length = 60)
    private String countryOfOrigin;
    @Column(name = "state_of_origin", length = 60)
    private String stateOfOrigin;

    @Column(name = "net_quantity", precision = 12, scale = 3)
    private BigDecimal netQuantity;
    @Column(name = "gross_quantity", precision = 12, scale = 3)
    private BigDecimal grossQuantity;
    @Column(name = "quantity_unit", length = 20)
    private String quantityUnit;

    @Column(name = "mfg_date")
    private LocalDate mfgDate;
    @Column(name = "batch_number", length = 60)
    private String batchNumber;

    /** The supplier has agreed in writing that their photos may be used. */
    @Column(name = "photos_allowed", nullable = false)
    private boolean photosAllowed = false;

    @Column(name = "confirmed_by", length = 120)
    private String confirmedBy;
}
