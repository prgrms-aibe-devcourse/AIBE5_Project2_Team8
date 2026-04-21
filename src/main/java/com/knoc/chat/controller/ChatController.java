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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final MemberRepository memberRepository;
    private final ChatRoomService chatRoomService;

    @GetMapping("/{roomId}")
    public String room(@PathVariable Long roomId, Model model, Principal principal) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));

        List<ChatMessage> messages = chatMessageRepository.findByChatRoomOrderByCreatedAtAsc(chatRoom);

        Member currentMember = memberRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        model.addAttribute("roomId", roomId);
        model.addAttribute("messages", messages);
        model.addAttribute("currentNickname", currentMember.getNickname());

        return "chat/chatroom";
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

    @PostMapping("/rooms")
    public String createChatRoom(Principal principal, @RequestParam Long seniorId) {
        ChatRoom chatRoom = chatRoomService.createChatRoom(principal, seniorId);

        return "redirect:/chat/" + chatRoom.getId();
    }

}
