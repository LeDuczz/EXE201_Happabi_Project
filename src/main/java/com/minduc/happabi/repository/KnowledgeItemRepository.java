package com.minduc.happabi.repository;

import com.minduc.happabi.entity.KnowledgeItem;
import com.minduc.happabi.enums.KnowledgeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KnowledgeItemRepository extends JpaRepository<KnowledgeItem, UUID> {

    List<KnowledgeItem> findByStatusOrderByCreatedAtDesc(KnowledgeStatus status);

    Optional<KnowledgeItem> findFirstByQuestionIgnoreCaseAndStatusOrderByUpdatedAtDesc(String question,
                                                                                      KnowledgeStatus status);

    List<KnowledgeItem> findByStatusAndVectorIndexedFalseOrderByUpdatedAtDesc(KnowledgeStatus status);
}
