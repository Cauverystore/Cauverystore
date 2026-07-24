package com.cauverystore.repository;

import com.cauverystore.entities.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    List<RolePermission> findByRole(String role);
    void deleteByRoleAndPermissionId(String role, Long permissionId);
}