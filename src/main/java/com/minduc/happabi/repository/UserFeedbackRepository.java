package com.minduc.happabi.repository;

import com.minduc.happabi.entity.UserFeedback;
import com.minduc.happabi.enums.UserFeedbackStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface UserFeedbackRepository extends JpaRepository<UserFeedback, UUID> {

    Page<UserFeedback> findBySubmittedBy_IdOrderByCreatedAtDesc(UUID submittedByUserId, Pageable pageable);

    Page<UserFeedback> findByStatusOrderByCreatedAtDesc(UserFeedbackStatus status, Pageable pageable);

    Page<UserFeedback> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select feedback from UserFeedback feedback where feedback.id = :id")
    Optional<UserFeedback> findByIdForUpdate(@Param("id") UUID id);
}
