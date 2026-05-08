package com.minduc.happabi.repository;

import com.minduc.happabi.entity.MotherProfile;
import com.minduc.happabi.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MotherProfileRepository extends JpaRepository<MotherProfile, UUID> {
    Optional<MotherProfile> findByUser(User user);
}
