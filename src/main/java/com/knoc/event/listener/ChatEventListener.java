package com.knoc.event.listener;

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

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) // DB 커밋이 완료된 후 실행
    @Transactional(propagation = Propagation.REQUIRES_NEW) // 새 트랜잭션을 열어서 시스템 메시지를 DB에  저장
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
                .sender(null)
                .build();

        chatMessageRepository.save(systemMessage);

        Map<String, Object> payload = new HashMap<>();
        payload.put("messageId", systemMessage.getId());
        payload.put("roomId", event.roomId());
        payload.put("type", event.type().name());
        payload.put("customContent", content);  // js와 맞추기 위해 content -> customContent 변경
        payload.put("referenceId", event.referenceId());

        String destination = "/topic/chat/" + event.roomId();
        messagingTemplate.convertAndSend(destination, payload);

        System.out.println("System Message Broadcast: " + destination + " [" + event.type() + "]");
    }
}
