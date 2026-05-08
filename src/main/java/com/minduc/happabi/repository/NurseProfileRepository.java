package com.minduc.happabi.repository;

import com.minduc.happabi.entity.NurseProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NurseProfileRepository extends JpaRepository<NurseProfile, UUID> {
}
