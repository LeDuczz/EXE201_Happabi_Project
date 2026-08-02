package com.minduc.happabi.repository;

import com.minduc.happabi.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    Page<Conversation> findByUserIdOrderByUpdatedAtDesc(UUID userId, Pageable pageable);

    Page<Conversation> findByUserIdAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(UUID userId,
                                                                                    String title,
                                                                                    Pageable pageable);

    Optional<Conversation> findByIdAndUserId(UUID id, UUID userId);
}
