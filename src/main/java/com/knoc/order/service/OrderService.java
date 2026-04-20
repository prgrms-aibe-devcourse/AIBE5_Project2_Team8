package com.knoc.order.service;


import com.knoc.chat.entity.ChatRoom;
import com.knoc.chat.entity.ChatSystemEvent;
import com.knoc.chat.entity.MessageType;
import com.knoc.chat.repository.ChatRoomRepository;
import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import com.knoc.order.dto.OrderRequest;
import com.knoc.order.dto.OrderResponse;
import com.knoc.order.entity.Order;
import com.knoc.order.entity.OrderStatus;
import com.knoc.order.repository.OrderRepository;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
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
        private final ApplicationEventPublisher eventPublisher;

        public OrderResponse createOrderRequest(OrderRequest dto, Long seniorId) {
                // 1. 엔티티 조회 (채팅방, 주니어, 시니어)
                ChatRoom chatRoom = chatRoomRepository.findById(dto.getChatRoomId())
                                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));
                Member junior = memberRepository.findById(dto.getJuniorId())
                                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
                Member senior = memberRepository.findById(seniorId) // 시큐리티에서 넘겨받은 현재 사용자
                                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

                // 요청한 사람이 실제 해당 채팅방의 시니어가 맞는지 검증
                if (!chatRoom.getSenior().getId().equals(seniorId)) {
                        throw new BusinessException(ErrorCode.NOT_SENIOR_IN_ROOM);
                }

                // 2. 주문 번호 생성
                String orderNumber = "ORD-" + UUID.randomUUID();

                // 3. 주문 객체 생성 및 DB 저장
                Order order = Order.builder()
                                .orderNumber(orderNumber)
                                .chatRoom(chatRoom)
                                .junior(junior)
                                .senior(senior)
                                .amount(dto.getAmount())
                                .build();

                Order savedOrder = orderRepository.save(order);

                // 4. 결제 요청 시스템 메시지 생성 및 저장
                // 금액에 콤마 추가 (예: 55000 -> 55,000)
                String formattedAmount = String.format("%,d", dto.getAmount());
                String customMessage = "시니어님이 " + formattedAmount + "원 결제를 요청했습니다.\n결제를 완료하시면 상세 리뷰 요청서를 작성하실 수 있습니다.";
                eventPublisher.publishEvent(new ChatSystemEvent(
                        chatRoom.getId(),
                        MessageType.PAYMENT_REQUESTED,
                        customMessage,
                        savedOrder.getId()
                ));
                // 5. 저장된 주문을 클라이언트에게 보여줄 전용 응답 객체(DTO)로 변환
                return OrderResponse.from(savedOrder);
        }

        public OrderResponse payOrder(Long orderId, String idempotencyKey, Long juniorId) {
                // 입력 검증
                if (idempotencyKey == null || idempotencyKey.isBlank() || juniorId == null) {
                        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
                }

                // 주문 조회
                Order order = orderRepository.findById(orderId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
                // String lockKey = "pay:" + order.getOrderNumber(); // 주문 생성 단계에서의 lockKey와 동일
                // (같은 주문에 대한 작업을 같은 키로 직렬화)

                // 주니어 검증
                if (!order.getJunior().getId().equals(juniorId)) {
                        throw new BusinessException(ErrorCode.NOT_JUNIOR_FOR_ORDER);
                }

                // 상태 분기
                if (order.getStatus() == OrderStatus.PAID) {
                        return OrderResponse.from(order); // 결제 완료 메시지 중복 방지
                } else if (order.getStatus() == OrderStatus.PENDING) {
                        order.updateStatus(OrderStatus.PAID);
                } else { // 그 외의 상태(SETTLED, CANCELLED)는 에러
                        throw new BusinessException(ErrorCode.ORDER_CANNOT_BE_PAID);
                }
                // 저장 (낙관적 락 충돌 시: 재조회 후 멱등 성공/충돌 응답)
                final Order savedOrder;
                try {
                    savedOrder = orderRepository.saveAndFlush(order);
                } catch (OptimisticLockingFailureException e) {
                    Order latest = orderRepository.findById(orderId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
                    if (latest.getStatus() == OrderStatus.PAID) {
                        return OrderResponse.from(latest); // 이미 결제 처리 완료된 경우 멱등 성공 (200)
                    }
                    throw new BusinessException(ErrorCode.ORDER_PAYMENT_CONFLICT); // 아직 PAID가 아니라면 충돌(409)
                }

                String customMessage = "결제가 성공적으로 처리되었습니다.\n결제 금액은 구매 확정 시까지 Knoc에서 안전하게 보호합니다.";

                eventPublisher.publishEvent(new ChatSystemEvent(
                        savedOrder.getChatRoom().getId(),
                        MessageType.PAYMENT_COMPLETED,
                        customMessage,
                        savedOrder.getId()
                ));

                return OrderResponse.from(savedOrder);
        }
}
