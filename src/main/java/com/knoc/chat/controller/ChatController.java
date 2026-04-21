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
        ChatRoom chatRoom = chatRoomService.createChatRoom(principal, seniorId);

        return "redirect:/chat/" + chatRoom.getId();
    }

    @GetMapping("/{roomId}")
    public String room(@PathVariable("roomId") Long selectedRoomId, Model model, Principal principal) {
        ChatRoom chatRoom = chatRoomRepository.findById(selectedRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));
        List<ChatMessage> messages = chatMessageRepository.findByChatRoomOrderByCreatedAtAsc(chatRoom);

        Member currentMember = memberRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        List<ChatRoom> chatRooms = chatRoomRepository.findByJuniorOrSenior(currentMember, currentMember);

        if(!chatRoom.getJunior().getId().equals(currentMember.getId()) &&
        !chatRoom.getSenior().getId().equals(currentMember.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        Map<Long, ChatMessage> latestMessages = buildLatestMessages(chatRooms);

        model.addAttribute("selectedRoomId", selectedRoomId);
        model.addAttribute("messages", messages);
        model.addAttribute("currentNickname", currentMember.getNickname());
        model.addAttribute("rooms", chatRooms);
        model.addAttribute("selectedRoom", chatRoom);
        model.addAttribute("latestMessages", latestMessages);

        return "chat/chatrooms";
    }

    // Handshake에서 Principal 넘겨받음
    @Transactional
    @MessageMapping("/{roomId}/send")
    public void send(@DestinationVariable Long roomId, @Payload ChatMessageRequest request, Principal principal) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));

        Member sender = memberRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        ChatMessage message = ChatMessage.builder()
                .chatRoom(chatRoom)
                .sender(sender)
                .content(request.getContent())
                .messageType(MessageType.USER)
                .build();
        chatMessageRepository.save(message);

        ChatMessageResponse response = new ChatMessageResponse(sender.getNickname(), message.getContent(), message.getCreatedAt(), message.getMessageType());

        String receiverEmail = sender.getId().equals(chatRoom.getJunior().getId())
            ? chatRoom.getSenior().getEmail()
                : chatRoom.getJunior().getEmail();

        simpMessagingTemplate.convertAndSendToUser(receiverEmail, "/queue/chat", response);
        simpMessagingTemplate.convertAndSendToUser(sender.getEmail(), "/queue/chat", response);

    }

}
