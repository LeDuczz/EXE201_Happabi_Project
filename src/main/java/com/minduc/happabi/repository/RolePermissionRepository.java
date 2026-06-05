package com.minduc.happabi.repository;

import com.minduc.happabi.entity.RolePermission;
import com.minduc.happabi.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    @Query("""
        SELECT rp FROM RolePermission rp
        JOIN FETCH rp.permission p
        JOIN rp.role r
        WHERE r.roleName = :roleName
          AND r.isActive = true
          AND p.isActive = true
    """)
    List<RolePermission> findByRoleName(@Param("roleName") UserRole roleName);

}
