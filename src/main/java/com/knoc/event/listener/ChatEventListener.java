package com.knoc.event.listener;

import com.knoc.chat.dto.ChatMessageResponse;
import com.knoc.chat.entity.ChatMessage;
import com.knoc.chat.entity.ChatRoom;
import com.knoc.chat.entity.ChatSystemEvent;
import com.knoc.chat.entity.MessageType;
import com.knoc.chat.repository.ChatMessageRepository;
import com.knoc.chat.repository.ChatRoomRepository;
import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import com.knoc.order.entity.Order;
import com.knoc.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final OrderRepository orderRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleChatEventSystem(ChatSystemEvent event) {
        log.info("[System Event Received] Room ID: {}, Type: {}", event.roomId(), event.type());
        ChatRoom chatRoom = chatRoomRepository.findById(event.roomId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));

        String content = (event.customContent() != null && !event.customContent().isBlank())
                ? event.customContent() : event.type().getTemplate();

        // 1. 시스템 메시지 엔티티 생성 및 DB 저장
        ChatMessage systemMessage = ChatMessage.builder()
                .chatRoom(chatRoom)
                .messageType(event.type())
                .content(content)
                .referenceId(event.referenceId())
                .sender(null) // 시스템 알림이므로 발신자는 null
                .build();

        chatMessageRepository.save(systemMessage);

        // 2. PAYMENT_REQUESTED 타입은 프론트 결제 버튼 렌더링을 위해 amount를 조회해 실어 보냄
        Integer amount = resolveAmount(event);

        // 3. 프론트엔드와 맞춘 DTO 응답
        ChatMessageResponse response = ChatMessageResponse.builder()
                .id(systemMessage.getId())
                .senderNickname("SYSTEM")
                .content(content)
                .createdAt(systemMessage.getCreatedAt() != null ? systemMessage.getCreatedAt() : LocalDateTime.now())
                .messageType(event.type())
                .referenceId(event.referenceId())
                .amount(amount)
                .build();

        // 4. 1:1 Queue 방식으로 주니어, 시니어 양쪽에게 전송
        messagingTemplate.convertAndSendToUser(chatRoom.getJunior().getEmail(), "/queue/chat", response);
        messagingTemplate.convertAndSendToUser(chatRoom.getSenior().getEmail(), "/queue/chat", response);

        log.info("System Message Broadcast via Queue [" + event.type() + "]");
    }

    // PAYMENT_REQUEST 이벤트에만 amount를 주입한다. (그 외의 이벤트는 즉시 null 반환)
    // Order 조회 실패 시 null을 반환해 메시지 브로드캐스트 자체는 중단시키지 않는다.
    private Integer resolveAmount(ChatSystemEvent event) {
        if (event.type() != MessageType.PAYMENT_REQUESTED) return null;
        if (event.referenceId() == null) return null;

        return orderRepository.findById(event.referenceId())
                .map(Order::getAmount)
                .orElseGet(() -> {
                    log.warn("PAYMENT_REQUESTED 이벤트의 referenceId={}에 해당하는 Order를 찾지 못해 amount를 null로 전송합니다.",
                            event.referenceId());
                    return null;
                });
    }
}