package com.minduc.happabi.repository;

import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("SELECT FUNCTION('DATE', u.createdAt) as createdDate, COUNT(u) as count " +
            "FROM User u " +
            "WHERE u.createdAt >= :startDate " +
            "GROUP BY FUNCTION('DATE', u.createdAt) " +
            "ORDER BY FUNCTION('DATE', u.createdAt)")
    List<Object[]> getUserGrowthStats(@Param("startDate") Instant startDate);

    Optional<User> findByPhone(String phone);

    Optional<User> findByEmail(String email);

    Optional<User> findByCognitoSub(String cognitoSub);

    Optional<User> findByCognitoUsername(String cognitoUsername);

    @Query("""
                SELECT u FROM User u
                LEFT JOIN FETCH u.roleAssignments ra
                LEFT JOIN FETCH ra.role
                LEFT JOIN FETCH u.identityProviders
                WHERE u.cognitoSub = :cognitoSub
            """)
    Optional<User> findByCognitoSubWithRolesAndProviders(@Param("cognitoSub") String cognitoSub);

    @Query("""
                SELECT u FROM User u
                LEFT JOIN FETCH u.roleAssignments ra
                LEFT JOIN FETCH ra.role
                LEFT JOIN FETCH u.identityProviders
                WHERE u.phone = :phone
            """)
    Optional<User> findByPhoneWithRolesAndProviders(@Param("phone") String phone);

    @Query("""
                SELECT u FROM User u
                LEFT JOIN FETCH u.roleAssignments ra
                LEFT JOIN FETCH ra.role
                LEFT JOIN FETCH u.identityProviders
                WHERE u.email = :email
            """)
    Optional<User> findByEmailWithRolesAndProviders(@Param("email") String email);

    @Query("SELECT COUNT(u) FROM User u JOIN u.roleAssignments ra WHERE ra.role.roleName = :roleName")
    long countByRoleName(@Param("roleName") UserRole roleName);

    @Query("SELECT u FROM User u JOIN u.roleAssignments ra WHERE ra.role.roleName = :roleName AND u.isActive = true")
    List<User> findActiveUsersByRoleName(@Param("roleName") UserRole roleName);

    Page<User> findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrPhoneContaining(
            String fullName, String email, String phone, org.springframework.data.domain.Pageable pageable);
}
