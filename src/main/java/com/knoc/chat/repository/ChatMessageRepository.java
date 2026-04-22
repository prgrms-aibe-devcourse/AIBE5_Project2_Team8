package com.knoc.chat.repository;

import com.knoc.chat.entity.ChatMessage;
import com.knoc.chat.entity.MessageType;
import com.knoc.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;

import java.util.List;

/** ChatMessageRepository
 * 채팅 메시지 조회 쿼리 메서드
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    // 특정 주문에 대해 지정 시각 이후 동일 타입 메시지가 존재하는지 확인
    boolean existsByReferenceIdAndMessageTypeAndCreatedAtAfter(
            Long referenceId, MessageType messageType, LocalDateTime threshold);
  
    // 채팅방의 메시지를 작성된 시간 기준으로 오름차순 정렬하여 전체 조회
    List<ChatMessage> findByChatRoomOrderByCreatedAtAsc(ChatRoom chatRoom);

    // 가장 최근에 작성된 채팅 메시지 1건 조회
    ChatMessage findFirstByChatRoomOrderByCreatedAtDesc(ChatRoom chatRoom);
}
