package com.minduc.happabi.repository;

import com.minduc.happabi.entity.NurseReview;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface NurseReviewRepository extends JpaRepository<NurseReview, UUID> {

    boolean existsByWorkSession_Id(UUID workSessionId);

    @EntityGraph(attributePaths = {"workSession", "nurseProfile", "nurseProfile.user", "mother", "tags"})
    Optional<NurseReview> findByWorkSession_IdAndMother_Id(UUID workSessionId, UUID motherId);

    @Query("""
            select avg(nr.rating) as averageRating,
                   count(nr) as totalReviews
            from NurseReview nr
            where nr.nurseProfile.id = :nurseProfileId
            """)
    NurseRatingAggregate calculateAggregate(@Param("nurseProfileId") UUID nurseProfileId);

    @Query("select avg(nr.rating) from NurseReview nr where nr.mother.id = :motherId")
    Double averageRatingGivenByMother(@Param("motherId") UUID motherId);

    interface NurseRatingAggregate {
        Double getAverageRating();
        Long getTotalReviews();
    }
}
