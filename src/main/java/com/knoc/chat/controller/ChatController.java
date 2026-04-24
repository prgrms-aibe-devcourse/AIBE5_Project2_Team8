package com.knoc.chat.controller;

import com.knoc.chat.dto.ChatMessageRequest;
import com.knoc.chat.dto.ChatMessageResponse;
import com.knoc.chat.dto.ChatRoomDetailDto;
import com.knoc.chat.dto.ChatRoomListDto;
import com.knoc.chat.entity.ChatMessage;
import com.knoc.chat.entity.ChatRoom;
import com.knoc.chat.entity.MessageType;
import com.knoc.chat.service.ChatMessageService;
import com.knoc.chat.service.ChatRoomService;
import com.knoc.order.service.OrderService;
import com.knoc.senior.repository.SeniorProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatMessageService chatMessageService;
    private final ChatRoomService chatRoomService;
    private final OrderService orderService;
    private final SeniorProfileRepository seniorProfileRepository;

    @Value("${toss.payments.client-key:}")
    private String tossClientKey;

    @GetMapping("/rooms")
    public String getChatRoomsPage(Model model, Principal principal) {

        ChatRoomListDto dto = chatRoomService.getChatRoomListDto(principal.getName());

        model.addAttribute("rooms", dto.rooms());
        model.addAttribute("latestMessages", dto.latestMessages());
        model.addAttribute("currentNickname", dto.currentNickname());
        model.addAttribute("selectedRoomId", null);

        return "chat/chatrooms";
    }

    @PostMapping("/rooms")
    public String createChatRoom(Principal principal, @RequestParam Long seniorId) {
        ChatRoom chatRoom = chatRoomService.createChatRoom(principal.getName(), seniorId);

        return "redirect:/chat/" + chatRoom.getId();
    }

    @GetMapping("/{roomId}")
    public String getChatRoomPage(@PathVariable("roomId") Long roomId, Model model, Principal principal) {
        ChatRoomDetailDto dto = chatRoomService.getRoomDetailInfo(roomId, principal.getName());
        ChatRoom chatRoom = dto.selectedRoom();
        List<ChatMessage> messages = dto.messages();

        boolean isSenior = chatRoom.getSenior() != null
                && chatRoom.getSenior().getEmail() != null
                && chatRoom.getSenior().getEmail().equals(principal.getName());
        Long juniorId = chatRoom.getJunior() != null ? chatRoom.getJunior().getId() : null;

        if (isSenior && messages != null && !messages.isEmpty()) {
            messages = messages.stream()
                    .filter(m -> m.getMessageType() != MessageType.REVIEW_REQUESTED)
                    .toList();
        }

        Map<Long, Integer> orderAmounts = orderService.extractOrderAmounts(messages);

        boolean hasPaymentRequest = orderService.hasActivePaymentRequest(chatRoom);

        int seniorPricePerReview = seniorProfileRepository.findByMemberId(chatRoom.getSenior().getId())
                .map(p -> p.getPricePerReview())
                .orElse(0);

        model.addAttribute("selectedRoomId", dto.selectedRoomId());
        model.addAttribute("messages", messages);
        model.addAttribute("currentNickname", dto.currentNickname());
        model.addAttribute("rooms", dto.rooms());
        model.addAttribute("selectedRoom", dto.selectedRoom());
        model.addAttribute("firstMessageId", dto.firstMessageId());
        model.addAttribute("latestMessages", dto.latestMessages());
        model.addAttribute("roomStatus", dto.roomStatus());

        model.addAttribute("isSenior", isSenior);
        model.addAttribute("juniorId", juniorId);
        model.addAttribute("orderAmounts", orderAmounts);
        model.addAttribute("hasPaymentRequest", hasPaymentRequest);
        model.addAttribute("seniorPricePerReview", seniorPricePerReview);
        model.addAttribute("tossClientKey", tossClientKey);

        return "chat/chatrooms";
    }

    @MessageMapping("/{roomId}/send")
    public void sendMessage(@DestinationVariable Long roomId, @Payload ChatMessageRequest request, Principal principal) {
        chatMessageService.sendMessage(roomId, principal.getName(), request.getContent());
    }

    @GetMapping("/{roomId}/messages")
    @ResponseBody
    public List<ChatMessageResponse> getMessagesBefore(@PathVariable("roomId") Long roomId, @RequestParam Long before, Principal principal) {
        return chatMessageService.getPreviousMessages(roomId, before, principal.getName());
    }
}