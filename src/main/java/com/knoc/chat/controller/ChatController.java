package com.knoc.chat.controller;

import com.knoc.chat.dto.ChatMessageRequest;
import com.knoc.chat.dto.ChatMessageResponse;
import com.knoc.chat.dto.ChatRoomDetailDto;
import com.knoc.chat.dto.ChatRoomListDto;
import com.knoc.chat.entity.ChatRoom;
import com.knoc.chat.service.ChatMessageService;
import com.knoc.chat.service.ChatRoomService;
import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import com.knoc.member.Member;
import com.knoc.order.entity.Order;
import com.knoc.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@Controller
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatMessageService chatMessageService;
    private final ChatRoomService chatRoomService;
    private final OrderRepository orderRepository;


    // 메시지 목록에서 PAYMENT_REQUESTED 타입만 골라 (orderId, amount) 맵을 구성한다.
    // Thymeleaf 첫 렌더링과 페이지네이션 응답 양쪽에서 결제 버튼 금액 표시에 사용.
    private Map<Long, Integer> buildOrderAmounts(List<ChatMessage> messages) {
        List<Long> paymentOrderIds = messages.stream()
                .filter(m -> m.getMessageType() == MessageType.PAYMENT_REQUESTED && m.getReferenceId() != null)
                .map(ChatMessage::getReferenceId)
                .toList();

        if (paymentOrderIds.isEmpty()) return Collections.emptyMap();

        return orderRepository.findAllById(paymentOrderIds).stream()
                .collect(Collectors.toMap(Order::getId, Order::getAmount));
    }

    @GetMapping("/rooms")
    public String rooms(Model model, Principal principal) {

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
    public String room(@PathVariable("roomId") Long roomId, Model model, Principal principal) {
        ChatRoomDetailDto dto = chatRoomService.getRoomDetailInfo(roomId, principal.getName());
        // 7. 현재 사용자의 역할 및 상대 주니어 ID 계산
        boolean isSenior = chatRoom.getSenior().getId().equals(currentMember.getId());
        Long juniorId = chatRoom.getJunior().getId();

        // 8. 이번 화면에 뿌릴 PAYMENT_REQUESTED 메시지들의 금액 맵 구성
        Map<Long, Integer> orderAmounts = buildOrderAmounts(messages);

        // 9. 해당 채팅방에 이미 결제 요청(Order)이 있었는지 (시니어 헤더 버튼 초기 노출 제어)
        boolean hasPaymentRequest = orderRepository.existsByChatRoom_Id(selectedRoomId);

        model.addAttribute("selectedRoomId", dto.selectedRoomId());
        model.addAttribute("messages", dto.messages());
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

        return "chat/chatrooms";
    }

    @MessageMapping("/{roomId}/send")
    public void send(@DestinationVariable Long roomId, @Payload ChatMessageRequest request, Principal principal) {
        // Principal(이메일)만 넘기고 유저 찾는 로직도 Service로 이동하면 더 깔끔해집니다!
        chatMessageService.sendMessage(roomId, principal.getName(), request.getContent());
    }

    @GetMapping("/{roomId}/messages")
    @ResponseBody
    public List<ChatMessageResponse> getMessages(@PathVariable("roomId") Long roomId, @RequestParam Long before, Principal principal) {
        return chatMessageService.getPreviousMessages(roomId, before, principal.getName());
    }
}
