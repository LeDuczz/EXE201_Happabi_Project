package com.minduc.happabi.repository;

import com.minduc.happabi.entity.PlatformRevenue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;

@Repository
public interface PlatformRevenueRepository extends JpaRepository<PlatformRevenue, String> {

    @Query("""
            select coalesce(sum(revenue.amount), 0)
            from PlatformRevenue revenue
            where revenue.createdAt between :startAt and :endAt
            """)
    BigDecimal sumAmountByCreatedAtBetween(@Param("startAt") Instant startAt,
                                           @Param("endAt") Instant endAt);
}
