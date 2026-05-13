package com.minduc.happabi.repository;

import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByPhone(String phone);

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

}
