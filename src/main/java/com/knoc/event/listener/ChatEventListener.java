package com.knoc.event.listener;

import com.knoc.chat.dto.ChatMessageResponse;
import com.knoc.chat.entity.ChatMessage;
import com.knoc.chat.entity.ChatRoom;
import com.knoc.chat.entity.ChatSystemEvent;
import com.knoc.chat.repository.ChatMessageRepository;
import com.knoc.chat.repository.ChatRoomRepository;
import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleChatEventSystem(ChatSystemEvent event) {
        log.info("[System Event Received] Room ID: {}, Type: {}", event.roomId(), event.type());
        ChatRoom chatRoom = chatRoomRepository.findById(event.roomId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));

        String content = (event.customContent() != null && !event.customContent().isBlank())
                ? event.customContent() : event.type().getTemplate();

        ChatMessage systemMessage = ChatMessage.builder()
                .chatRoom(chatRoom)
                .messageType(event.type())
                .content(content)
                .referenceId(event.referenceId())
                .sender(null) // 시스템
                .build();

        chatMessageRepository.save(systemMessage);

        // 프론트엔드와 맞춘 DTO 응답
        ChatMessageResponse response = ChatMessageResponse.builder()
                .senderNickname("SYSTEM")
                .content(content)
                .createdAt(systemMessage.getCreatedAt() != null ? systemMessage.getCreatedAt() : LocalDateTime.now())
                .messageType(event.type())
                .build();

        // 1:1 Queue 방식으로 주니어, 시니어 양쪽에게 전송
        messagingTemplate.convertAndSendToUser(chatRoom.getJunior().getEmail(), "/queue/chat", response);
        messagingTemplate.convertAndSendToUser(chatRoom.getSenior().getEmail(), "/queue/chat", response);

        log.info("System Message Broadcast via Queue [" + event.type() + "]");
    }
}