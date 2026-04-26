package com.knoc.chat.repository;

import com.knoc.chat.entity.ChatMessage;
import com.knoc.chat.entity.MessageType;
import com.knoc.chat.entity.ChatRoom;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // 여러 채팅방의 최신 메시지를 쿼리 1번으로 조회 (N+1 방지)
    @Query("SELECT m FROM ChatMessage m WHERE m.id IN (" +
           "SELECT MAX(m2.id) FROM ChatMessage m2 WHERE m2.chatRoom.id IN :roomIds GROUP BY m2.chatRoom.id)")
    List<ChatMessage> findLatestMessagesForRooms(@Param("roomIds") List<Long> roomIds);

    // 특정 타입을 제외한 가장 최근 메시지 1건 조회 (사이드바 preview 필터링용)
    ChatMessage findFirstByChatRoomAndMessageTypeNotOrderByCreatedAtDesc(ChatRoom chatRoom, MessageType messageType);

    List<ChatMessage> findByChatRoomAndIdLessThanOrderByIdDesc(ChatRoom chatRoom, Long before, PageRequest of);
}
