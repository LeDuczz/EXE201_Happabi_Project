package com.minduc.happabi.repository;

import com.minduc.happabi.entity.ChatMessage;
import com.minduc.happabi.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findByConversationOrderByCreatedAtAsc(Conversation conversation);

    List<ChatMessage> findTop12ByConversationOrderByCreatedAtDesc(Conversation conversation);
}
