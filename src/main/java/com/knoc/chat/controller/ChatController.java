package com.knoc.chat.controller;

import com.knoc.chat.dto.ChatMessageRequest;
import com.knoc.chat.dto.ChatMessageResponse;
import com.knoc.chat.dto.ChatRoomDetailDto;
import com.knoc.chat.dto.ChatRoomListDto;
import com.knoc.chat.entity.ChatMessage;
import com.knoc.chat.entity.ChatRoom;
import com.knoc.chat.service.ChatMessageService;
import com.knoc.chat.service.ChatRoomService;
import com.knoc.order.service.OrderService;
import com.knoc.senior.repository.SeniorProfileRepository;
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
    private final OrderService orderService;
    private final SeniorProfileRepository seniorProfileRepository;

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

        // 현재 사용자의 역할 및 상대 주니어 ID 계산
        boolean isSenior = chatRoom.getSenior() != null
                && chatRoom.getSenior().getEmail() != null
                && chatRoom.getSenior().getEmail().equals(principal.getName());
        Long juniorId = chatRoom.getJunior() != null ? chatRoom.getJunior().getId() : null;

        // PAYMENT_REQUESTED 메시지들의 금액 맵 구성 (orderId -> amount)
        Map<Long, Integer> orderAmounts = orderService.extractOrderAmounts(messages);

        // 해당 채팅방에 "현재 진행 중인" 결제 요청(Order)이 있는지 확인 (시니어 헤더 버튼 초기 노출 제어)
        // PENDING(결제 대기), PAID(결제 완료/진행 중) 상태인 주문이 있다면 결제 버튼을 숨김.
        // SETTLED(완료)나 CANCELLED(취소)된 과거 주문은 무시.
        boolean hasPaymentRequest = orderService.hasActivePaymentRequest(chatRoom);

        // 이 채팅방 시니어의 등록 리뷰 단가 (결제 요청 모달 placeholder용)
        // 프로필 미등록/미설정 시 0 → 프런트에서 기본값으로 처리
        int seniorPricePerReview = seniorProfileRepository.findByMemberId(chatRoom.getSenior().getId())
                .map(p -> p.getPricePerReview())
                .orElse(0);

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
        model.addAttribute("seniorPricePerReview", seniorPricePerReview);

        return "chat/chatrooms";
    }

    @MessageMapping("/{roomId}/send")
    public void sendMessage(@DestinationVariable Long roomId, @Payload ChatMessageRequest request, Principal principal) {
        // Principal(이메일)만 넘기고 유저 찾는 로직도 Service로 이동하면 더 깔끔해집니다!
        chatMessageService.sendMessage(roomId, principal.getName(), request.getContent());
    }

    @GetMapping("/{roomId}/messages")
    @ResponseBody
    public List<ChatMessageResponse> getMessagesBefore(@PathVariable("roomId") Long roomId, @RequestParam Long before, Principal principal) {
        return chatMessageService.getPreviousMessages(roomId, before, principal.getName());
    }
}
