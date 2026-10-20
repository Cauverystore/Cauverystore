package com.cauverystore.entities;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A role a seller can give to people on their team: what parts of the seller panel they may use.
 *
 * Built-in roles (OWNER, ADMIN, PRODUCT_MANAGER, ORDER_MANAGER, ACCOUNTANT, CUSTOMER_SUPPORT,
 * WAREHOUSE_MANAGER) have no seller and cannot be changed. A seller may add their own roles,
 * which belong to that seller only.
 */
@Entity
@Table(name = "seller_team_roles", indexes = {
        @Index(name = "idx_seller_team_roles_seller", columnList = "seller_id")
})
public class SellerTeamRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null for a built-in role shared by every seller. */
    @Column(name = "seller_id")
    private Long sellerId;

    @Column(name = "code", length = 40, nullable = false)
    private String code;

    @Column(name = "name", length = 80, nullable = false)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "built_in", nullable = false)
    private Boolean builtIn = false;

    /** The role-permission mapping: permission codes from SellerPermissions. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "seller_team_role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission", length = 40, nullable = false)
    private Set<String> permissions = new LinkedHashSet<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Boolean getBuiltIn() { return builtIn; }
    public void setBuiltIn(Boolean builtIn) { this.builtIn = builtIn; }
    public Set<String> getPermissions() { return permissions; }
    public void setPermissions(Set<String> permissions) { this.permissions = permissions; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
