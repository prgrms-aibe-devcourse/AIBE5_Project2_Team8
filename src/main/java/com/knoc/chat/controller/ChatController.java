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
import com.knoc.order.entity.Order;
import com.knoc.order.repository.OrderRepository;
import com.knoc.senior.repository.SeniorProfileRepository;
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
import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "Chat Controller", description = "채팅방 목록, 상세 조회 및 메시지 관리 관련 API")
@Controller
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatMessageService chatMessageService;
    private final ChatRoomService chatRoomService;
    private final OrderRepository orderRepository;
    private final SeniorProfileRepository seniorProfileRepository;

    @Value("${toss.payments.client-key:}")
    private String tossClientKey;


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

        // 현재 사용자의 역할 및 상대 주니어 ID 계산
        boolean isSenior = chatRoom.getSenior() != null
                && chatRoom.getSenior().getEmail() != null
                && chatRoom.getSenior().getEmail().equals(principal.getName());
        Long juniorId = chatRoom.getJunior() != null ? chatRoom.getJunior().getId() : null;

        // 주니어 전용 시스템 메시지(REVIEW_REQUESTED)는 시니어 화면에서는 노출하지 않는다.
        if (isSenior && messages != null && !messages.isEmpty()) {
            messages = messages.stream()
                    .filter(m -> m.getMessageType() != MessageType.REVIEW_REQUESTED)
                    .toList();
        }

        // PAYMENT_REQUESTED 메시지들의 금액 맵 구성 (orderId -> amount)
        Map<Long, Integer> orderAmounts = buildOrderAmounts(messages);

        // 해당 채팅방에 이미 결제 요청 시스템 메시지가 있었는지 여부
        // - 버튼 초기 노출 제어에만 사용
        // - DB에 Order가 남아있더라도, "결제 요청 메시지"가 없다면 버튼은 노출할 수 있다.
        boolean hasPaymentRequest = messages != null && messages.stream()
                .anyMatch(m -> m.getMessageType() == MessageType.PAYMENT_REQUESTED);

        // 이 채팅방 시니어의 등록 리뷰 단가 (결제 요청 모달 placeholder용)
        // 프로필 미등록/미설정 시 0 → 프런트에서 기본값으로 처리
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
