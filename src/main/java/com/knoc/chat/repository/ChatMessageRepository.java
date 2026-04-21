package com.knoc.chat.repository;

import com.knoc.chat.entity.ChatMessage;
import com.knoc.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByChatRoomOrderByCreatedAtAsc(ChatRoom chatRoom);

    ChatMessage findFirstByChatRoomOrderByCreatedAtDesc(ChatRoom chatRoom);
}
