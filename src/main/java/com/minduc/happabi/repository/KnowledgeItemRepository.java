package com.minduc.happabi.repository;

import com.minduc.happabi.entity.KnowledgeItem;
import com.minduc.happabi.enums.KnowledgeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KnowledgeItemRepository extends JpaRepository<KnowledgeItem, UUID> {

    List<KnowledgeItem> findByStatusOrderByCreatedAtDesc(KnowledgeStatus status);

    List<KnowledgeItem> findAllByOrderByCreatedAtDesc();

    Page<KnowledgeItem> findByStatusOrderByCreatedAtDesc(KnowledgeStatus status, Pageable pageable);

    Page<KnowledgeItem> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("""
            select item
            from KnowledgeItem item
            where (:status is null or item.status = :status)
              and (:keyword is null
                   or lower(item.title) like lower(concat('%', :keyword, '%'))
                   or lower(item.question) like lower(concat('%', :keyword, '%'))
                   or lower(item.answer) like lower(concat('%', :keyword, '%')))
            order by item.createdAt desc
            """)
    Page<KnowledgeItem> searchItems(KnowledgeStatus status, String keyword, Pageable pageable);

    long countByStatus(KnowledgeStatus status);

    Optional<KnowledgeItem> findFirstByQuestionIgnoreCaseAndStatusOrderByUpdatedAtDesc(String question,
                                                                                      KnowledgeStatus status);

    List<KnowledgeItem> findByStatusAndVectorIndexedFalseOrderByUpdatedAtDesc(KnowledgeStatus status);
}
