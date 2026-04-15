package com.knoc.order.service;

import com.knoc.chat.entity.ChatRoom;
import com.knoc.chat.repository.ChatRoomRepository;
import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import com.knoc.order.dto.OrderRequest;
import com.knoc.order.dto.OrderResponse;
import com.knoc.order.entity.Order;
import com.knoc.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final MemberRepository memberRepository;

    public OrderResponse createOrderRequest(OrderRequest dto, Long seniorId) {
        // 1. 엔티티 조회 (채팅방, 주니어, 시니어)
        ChatRoom chatRoom = chatRoomRepository.findById(dto.getChatRoomId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));
        Member junior = memberRepository.findById(dto.getJuniorId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Member senior = memberRepository.findById(seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // 요청한 사람이 실제 해당 채팅방의 시니어가 맞는지 검증
        if (!chatRoom.getSenior().getId().equals(seniorId)) {
            throw new BusinessException(ErrorCode.NOT_SENIOR_IN_ROOM);
        }

        // 2. 주문 번호 생성
        String orderNumber = "ORD-" + UUID.randomUUID().toString();

        // 3. 저장
        Order order = Order.builder()
                .orderNumber(orderNumber)
                .chatRoom(chatRoom)
                .junior(junior)
                .senior(senior)
                .amount(dto.getAmount())
                .build();

        return OrderResponse.from(orderRepository.save(order));
    }
}
