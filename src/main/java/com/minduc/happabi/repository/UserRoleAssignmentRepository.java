package com.minduc.happabi.repository;

import com.minduc.happabi.entity.Role;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.entity.UserRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, UUID> {

    boolean existsByUserAndRole(User user, Role role);

    Optional<UserRoleAssignment> findByUserAndRole(User user, Role role);

}
