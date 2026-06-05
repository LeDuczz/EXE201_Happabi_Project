package com.minduc.happabi.repository;

import com.minduc.happabi.entity.NurseCertification;
import com.minduc.happabi.entity.NurseProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NurseCertificationRepository extends JpaRepository<NurseCertification, UUID> {
    List<NurseCertification> findByNurseOrderByIdDesc(NurseProfile nurse);
    long countByNurse(NurseProfile nurse);
}
