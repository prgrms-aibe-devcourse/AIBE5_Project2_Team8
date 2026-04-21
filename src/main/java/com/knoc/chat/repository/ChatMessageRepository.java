package com.knoc.chat.repository;

import com.knoc.chat.entity.ChatMessage;
import com.knoc.chat.entity.MessageType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    // 특정 주문에 대해 지정 시각 이후 동일 타입 메시지가 존재하는지 확인
    boolean existsByReferenceIdAndMessageTypeAndCreatedAtAfter(
            Long referenceId, MessageType messageType, LocalDateTime threshold);
}
