package com.minduc.happabi.repository;

import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.NurseStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NurseProfileRepository extends JpaRepository<NurseProfile, UUID> {

    @EntityGraph(attributePaths = {"user"})
    Optional<NurseProfile> findByUser(User user);

    @EntityGraph(attributePaths = {"user"})
    List<NurseProfile> findByNurseStatusOrderByUpdatedAtAsc(NurseStatus nurseStatus);

    List<NurseProfile> findByNurseStatus(NurseStatus nurseStatus, Pageable pageable);

}
