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
import com.knoc.senior.SeniorProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Chat Controller", description = "채팅방 목록, 상세 조회 및 메시지 관리 관련 API")
@Controller
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatMessageService chatMessageService;
    private final ChatRoomService chatRoomService;
    private final OrderService orderService;
    private final SeniorProfileService seniorProfileService;

    @Value("${toss.payments.client-key:}")
    private String tossClientKey;

    @Operation(summary = "채팅 목록 페이지 조회", description = "현재 로그인한 사용자의 전체 채팅방 목록 페이지를 조회합니다.")
    @GetMapping("/rooms")
    public String getChatRoomsPage(Model model, Principal principal) {

        ChatRoomListDto dto = chatRoomService.getChatRoomListDto(principal.getName());

        model.addAttribute("rooms", dto.rooms());
        model.addAttribute("latestMessages", dto.latestMessages());
        model.addAttribute("currentNickname", dto.currentNickname());
        model.addAttribute("selectedRoomId", null);

        return "chat/chatrooms";
    }

    @Operation(summary = "채팅방 생성", description = "대상 시니어와 새로운 채팅방을 생성하고 해당 방으로 이동합니다.")
    @PostMapping("/rooms")
    public String createChatRoom(Principal principal, @RequestParam Long seniorId) {
        ChatRoom chatRoom = chatRoomService.createChatRoom(principal.getName(), seniorId);

        return "redirect:/chat/" + chatRoom.getId();
    }

    @Operation(summary = "채팅방 상세 페이지 조회", description = "선택한 채팅방의 정보, 메시지 내역, 결제 요청 상태 등을 포함한 상세 페이지를 조회합니다.")
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
        int seniorPricePerReview = seniorProfileService.getPricePerReview(chatRoom.getSenior().getId());

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

    @Operation(summary = "메시지 전송 (WebSocket)", description = "채팅방으로 메시지를 전송합니다. (WebSocket/STOMP)")
    @MessageMapping("/{roomId}/send")
    public void sendMessage(@DestinationVariable Long roomId, @Payload ChatMessageRequest request, Principal principal) {
        // Principal(이메일)만 넘기고 유저 찾는 로직도 Service로 이동하면 더 깔끔해집니다!
        chatMessageService.sendMessage(roomId, principal.getName(), request.getContent());
    }

    @Operation(summary = "이전 메시지 조회", description = "스크롤 시 이전 대화 내역을 가져오기 위한 API입니다.")
    @GetMapping("/{roomId}/messages")
    @ResponseBody
    public List<ChatMessageResponse> getMessagesBefore(@PathVariable("roomId") Long roomId, @RequestParam Long before, Principal principal) {
        return chatMessageService.getPreviousMessages(roomId, before, principal.getName());
    }
}