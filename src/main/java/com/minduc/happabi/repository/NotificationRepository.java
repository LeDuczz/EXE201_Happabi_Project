package com.minduc.happabi.repository;

import com.minduc.happabi.entity.Notification;
import com.minduc.happabi.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findTop30ByUserOrderByCreatedAtDesc(User user);

    long countByUserAndReadAtIsNull(User user);

    @Query("SELECT n FROM Notification n JOIN FETCH n.user WHERE n.id = :id")
    Optional<Notification> findByIdWithUser(@Param("id") UUID id);
}
