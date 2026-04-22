package com.knoc.chat.controller;

import com.knoc.chat.dto.ChatMessageRequest;
import com.knoc.chat.dto.ChatMessageResponse;
import com.knoc.chat.entity.ChatMessage;
import com.knoc.chat.entity.ChatRoom;
import com.knoc.chat.entity.MessageType;
import com.knoc.chat.repository.ChatMessageRepository;
import com.knoc.chat.repository.ChatRoomRepository;
import com.knoc.chat.service.ChatRoomService;
import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/chat")
@RequiredArgsConstructor
@Transactional
public class ChatController {
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final MemberRepository memberRepository;
    private final ChatRoomService chatRoomService;

    private Map<Long, ChatMessage> buildLatestMessages(List<ChatRoom> chatRooms) {
        Map<Long, ChatMessage> map = new HashMap<>();
        for (ChatRoom chatRoom : chatRooms) {
            map.put(chatRoom.getId(), chatMessageRepository.findFirstByChatRoomOrderByCreatedAtDesc(chatRoom));
        }
        return map;
    }

    private void verifyParticipant(ChatRoom chatRoom, Member currentMember){
        if(!chatRoom.getJunior().getId().equals(currentMember.getId()) &&
                !chatRoom.getSenior().getId().equals(currentMember.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    @GetMapping("/rooms")
    public String rooms(Model model, Principal principal) {
        Member user = memberRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        List<ChatRoom> chatRooms = chatRoomRepository.findByJuniorOrSenior(user, user);
        Map<Long, ChatMessage> latestMessages = buildLatestMessages(chatRooms);
        String currentNickname = user.getNickname();

        model.addAttribute("rooms", chatRooms);
        model.addAttribute("latestMessages", latestMessages);
        model.addAttribute("currentNickname", currentNickname);
        model.addAttribute("selectedRoomId", null);

        return "chat/chatrooms";
    }

    @PostMapping("/rooms")
    public String createChatRoom(Principal principal, @RequestParam Long seniorId) {
        ChatRoom chatRoom = chatRoomService.createChatRoom(principal.getName(), seniorId);

        return "redirect:/chat/" + chatRoom.getId();
    }

    @GetMapping("/{roomId}")
    public String room(@PathVariable("roomId") Long selectedRoomId, Model model, Principal principal) {
        // 1. 채팅방 존재 여부 검증
        ChatRoom chatRoom = chatRoomRepository.findById(selectedRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));

        // 2. 현재 로그인 유저 조회
        Member currentMember = memberRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // 3. 참여자 여부 검증
        verifyParticipant(chatRoom, currentMember);

        // 4. 내 채팅장 목록 (사이드바)
        List<ChatRoom> chatRooms = chatRoomRepository.findByJuniorOrSenior(currentMember, currentMember);

        // 5. 과거 메시지 전체 조회
        List<ChatMessage> messages = chatMessageRepository.findByChatRoomOrderByCreatedAtAsc(chatRoom);

        // 6. 사이드바 미리보기용 최신 메시지 Map
        Map<Long, ChatMessage> latestMessages = buildLatestMessages(chatRooms);

        model.addAttribute("selectedRoomId", selectedRoomId);
        model.addAttribute("messages", messages);
        model.addAttribute("currentNickname", currentMember.getNickname());
        model.addAttribute("rooms", chatRooms);
        model.addAttribute("selectedRoom", chatRoom);
        model.addAttribute("latestMessages", latestMessages);
        model.addAttribute("roomStatus", chatRoom.getStatus().name());

        return "chat/chatrooms";
    }

    // Handshake에서 Principal 넘겨받음
    @MessageMapping("/{roomId}/send")
    public void send(@DestinationVariable Long roomId, @Payload ChatMessageRequest request, Principal principal) {
        // 1. 채팅방 조회
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));

        if (chatRoom.getStatus().name().equals("CLOSED")) {
            throw new BusinessException(ErrorCode.CHATROOM_ALREADY_CLOSED);
        }

        // 2. 발신자 조회 (Principal에서 이메일 추출)
        Member sender = memberRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // 3. 참여자 여부 검증
        verifyParticipant(chatRoom, sender);

        // 4. ChatMessage 엔티티 생성 & 저장
        ChatMessage message = ChatMessage.builder()
                .chatRoom(chatRoom)
                .sender(sender)
                .content(request.getContent())
                .messageType(MessageType.USER)
                .build();
        chatMessageRepository.save(message);

        // 5. Response DTO 생성
        ChatMessageResponse response = new ChatMessageResponse(sender.getNickname(), message.getContent(), message.getCreatedAt(), message.getMessageType());

        // 6. 수신자/발신자 양쪽에 전송
        String receiverEmail = sender.getId().equals(chatRoom.getJunior().getId())
            ? chatRoom.getSenior().getEmail()
                : chatRoom.getJunior().getEmail();

        simpMessagingTemplate.convertAndSendToUser(receiverEmail, "/queue/chat", response);
        simpMessagingTemplate.convertAndSendToUser(sender.getEmail(), "/queue/chat", response);

    }
}
