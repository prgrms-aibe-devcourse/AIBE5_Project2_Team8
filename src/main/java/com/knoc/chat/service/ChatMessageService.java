package com.knoc.chat.service;

import com.knoc.chat.entity.ChatMessage;
import com.knoc.chat.entity.ChatRoom;
import com.knoc.chat.entity.ChatRoomStatus;
import com.knoc.chat.entity.MessageType;
import com.knoc.chat.repository.ChatMessageRepository;
import com.knoc.chat.repository.ChatRoomRepository;
import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatRoomRepository chatRoomRepository;
    private final MemberRepository memberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void sendMessage(Long roomId, Long senderId, String content) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));

        boolean isParticipant = chatRoom.getSenior().getId().equals(senderId) ||
                chatRoom.getJunior().getId().equals(senderId);
        if(!isParticipant) throw new BusinessException(ErrorCode.ACCESS_DENIED);

        if(chatRoom.getStatus() == ChatRoomStatus.CLOSED) {
            throw new BusinessException(ErrorCode.CHATROOM_ALREADY_CLOSED);
        }
        Member sender = memberRepository.findById(senderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        ChatMessage chatMessage = ChatMessage.builder()
                .chatRoom(chatRoom)
                .sender(sender)
                .messageType(MessageType.USER)
                .content(content)
                .referenceId(null)
                .build();

        ChatMessage savedMessage = chatMessageRepository.save(chatMessage);

        Map<String, Object> responsePayload = Map.of(
                "messageId", savedMessage.getId(),
                "type", savedMessage.getMessageType().name(),
                "content", savedMessage.getContent(),
                "senderId", savedMessage.getSender()
        );
        messagingTemplate.convertAndSend("/topic/chat" + roomId, responsePayload);
    }
}
