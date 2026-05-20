package com.minduc.happabi.repository;

import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.NurseReviewEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NurseReviewEventRepository extends JpaRepository<NurseReviewEvent, UUID> {
    List<NurseReviewEvent> findByNurseOrderByCreatedAtDesc(NurseProfile nurse);
}
