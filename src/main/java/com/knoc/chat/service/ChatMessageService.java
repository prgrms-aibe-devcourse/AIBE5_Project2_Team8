package com.knoc.chat.service;

import com.knoc.chat.dto.ChatMessageResponse;
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
import com.knoc.order.entity.Order;
import com.knoc.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatRoomRepository chatRoomRepository;
    private final MemberRepository memberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatRoomService chatRoomService;
    private final OrderRepository orderRepository;

    @Transactional
    public void sendMessage(Long roomId, String email, String content) {
        // 1. 채팅방 조회
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));

        if(chatRoom.getStatus() == ChatRoomStatus.CLOSED) {
            throw new BusinessException(ErrorCode.CHATROOM_ALREADY_CLOSED);
        }

        // 2. 발신자 조회
        Member sender = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // 3. 참여자 여부 검증
        chatRoomService.verifyParticipant(chatRoom, sender);

        // 4. ChatMessage 엔티티 생성 & 저장
        ChatMessage chatMessage = ChatMessage.builder()
                .chatRoom(chatRoom)
                .sender(sender)
                .messageType(MessageType.USER)
                .content(content)
                .referenceId(null)
                .build();

        ChatMessage savedMessage = chatMessageRepository.save(chatMessage);

        // 5. Response DTO 생성
        ChatMessageResponse responsePayload = ChatMessageResponse.builder()
                .id(savedMessage.getId())
                .senderNickname(sender.getNickname())
                .content(savedMessage.getContent())
                .createdAt(savedMessage.getCreatedAt())
                .messageType(savedMessage.getMessageType())
                .referenceId(savedMessage.getReferenceId()) // USER 메시지는 null
                // amount는 생략 -> null (PAYMENT_REQUESTED에서만 의미 있음)
        .build();

        // 6. 수신자/발신자 양쪽에 1:1 queue 전송
        String receiverEmail = sender.getId().equals(chatRoom.getJunior().getId())
                ? chatRoom.getSenior().getEmail()
                : chatRoom.getJunior().getEmail();

        messagingTemplate.convertAndSendToUser(receiverEmail, "/queue/chat", responsePayload);
        messagingTemplate.convertAndSendToUser(sender.getEmail(), "/queue/chat", responsePayload);
    }
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getPreviousMessages(Long roomId, Long before, String email) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        chatRoomService.verifyParticipant(chatRoom, member);
        List<ChatMessage> messages = chatMessageRepository
                .findByChatRoomAndIdLessThanOrderByIdDesc(chatRoom, before, PageRequest.of(0, 20));

        // 주니어 전용 시스템 메시지(REVIEW_REQUESTED)는 시니어 화면에서는 노출하지 않는다.
        boolean isSenior = chatRoom.getSenior() != null
                && chatRoom.getSenior().getId() != null
                && member.getId() != null
                && chatRoom.getSenior().getId().equals(member.getId());
        if (isSenior) {
            messages = messages.stream()
                    .filter(m -> m.getMessageType() != MessageType.REVIEW_REQUESTED)
                    .toList();
        }
        messages = new ArrayList<>(messages);
        Collections.reverse(messages);


        // 이번 페이지에 포함된 PAYMENT_REQUESTED 메시지들의 orderId만 모아 한 번에 금액 조회
        List<Long> orderIds = messages.stream()
                .filter(m -> m.getMessageType() == MessageType.PAYMENT_REQUESTED && m.getReferenceId() != null)
                .map(ChatMessage::getReferenceId)
                .toList();

        Map<Long, Integer> amountByOrderId = orderIds.isEmpty()
                ? Collections.emptyMap()
                : orderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, Order::getAmount));

        return messages.stream()
                .map(m -> ChatMessageResponse.builder()
                        .id(m.getId())
                        .senderNickname(m.getSender() != null ? m.getSender().getNickname() : "SYSTEM")
                        .content(m.getContent())
                        .createdAt(m.getCreatedAt())
                        .messageType(m.getMessageType())
                        .referenceId(m.getReferenceId())
                        .amount(m.getMessageType() == MessageType.PAYMENT_REQUESTED
                                ? amountByOrderId.get(m.getReferenceId()) : null)
                        .build())
                .collect(Collectors.toList());
    }
}