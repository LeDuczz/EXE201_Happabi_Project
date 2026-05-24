package com.minduc.happabi.repository;

import com.minduc.happabi.entity.PlatformRevenue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PlatformRevenueRepository extends JpaRepository<PlatformRevenue, UUID> {
}
