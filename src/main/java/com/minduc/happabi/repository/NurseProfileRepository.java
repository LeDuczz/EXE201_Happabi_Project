package com.minduc.happabi.repository;

import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NurseProfileRepository extends JpaRepository<NurseProfile, UUID> {

    @EntityGraph(attributePaths = {"user"})
    Optional<NurseProfile> findByUser(User user);

}
