package com.knoc.order.service;


import com.knoc.chat.entity.ChatMessage;
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
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
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
                String customMessage = MessageType.PAYMENT_REQUESTED.formatMessage(dto.getAmount());
                eventPublisher.publishEvent(new ChatSystemEvent(
                        chatRoom.getId(),
                        MessageType.PAYMENT_REQUESTED,
                        customMessage,
                        savedOrder.getId()
                ));
                // 5. 저장된 주문을 클라이언트에게 보여줄 전용 응답 객체(DTO)로 변환
                return OrderResponse.from(savedOrder);
        }

        // 결제창 호출 전 단계(사전 검증/조회)
        // 실제 결제 승인 후 처리는 confirmPayment(String, long) 메서드에서 수행
        public OrderResponse preparePayment(Long orderId, String idempotencyKey, Long juniorId) {
                // 입력 검증
                if (idempotencyKey == null || idempotencyKey.isBlank() || juniorId == null) {
                        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
                }

                // 주문 조회
                if (orderId == null) {
                        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
                }
                Order order = orderRepository.findById(orderId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

                // String lockKey = "pay:" + order.getOrderNumber(); // 주문 생성 단계에서의 lockKey와 동일
                // (같은 주문에 대한 작업을 같은 키로 직렬화)

                // 주니어 검증
                if (order.getJunior() == null || order.getJunior().getId() == null) {
                        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
                }
                if (!order.getJunior().getId().equals(juniorId)) {
                        throw new BusinessException(ErrorCode.NOT_JUNIOR_FOR_ORDER);
                }

                // 결제 가능 상태 검증
                if (order.getStatus() == OrderStatus.PAID) {
                        return OrderResponse.from(order); // 이미 결제 완료된 경우 멱등 성공 (200)
                }
                if (order.getStatus() != OrderStatus.PENDING) {
                        throw new BusinessException(ErrorCode.ORDER_CANNOT_BE_PAID); // 결제 불가능한 상태
                }

                return OrderResponse.from(order); // 결제 가능한 상태
        }

        // 토스페이먼츠 결제 승인(confirm) 성공 후 호출
        // tossOrderId는 결제 요청 시 넣은 주문번호로, OrderService의 orderNumber와 같아야 함
        // 로컬에 해당 주문이 없으면(예: 메인 테스트용 TEST-...) Optional.empty() 를 반환
        public Optional<OrderResponse> confirmPayment(String tossOrderId, long confirmedAmount) {
                if (tossOrderId == null || tossOrderId.isBlank()) {
                        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
                }
                Optional<Order> found = orderRepository.findByOrderNumber(tossOrderId);
                if (found.isEmpty()) {
                        return Optional.empty();
                }
                Order order = found.get();
                if (order.getAmount() != confirmedAmount) {
                        throw new BusinessException(ErrorCode.ORDER_PAYMENT_AMOUNT_MISMATCH); // 결제 금액 불일치
                }
                return Optional.of(markPaid(order)); // 결제 완료 처리
        }

        /**
         * 토스 결제 실패/취소 리다이렉트 후 호출합니다.
         * - 주문이 존재하면 채팅방에 시스템 메시지를 저장합니다.
         * - 주문이 없으면(예: TEST-...) 아무 것도 하지 않습니다.
         * - 주문 상태는 기본적으로 변경하지 않습니다(PENDING 유지).
         */
        public void recordPaymentFailure(String tossOrderId, String reason) {
                if (tossOrderId == null || tossOrderId.isBlank()) {
                        return; // 토스 fail 콜백에서 orderId가 없을 수 있어 조용히 무시
                }
                Optional<Order> found = orderRepository.findByOrderNumber(tossOrderId);
                if (found.isEmpty()) {
                        return;
                }
                Order order = found.get();

                String customMessage = (reason == null || reason.isBlank())
                        ? "결제가 취소되었거나 실패했습니다.\n다시 시도해주세요."
                        : "결제가 취소되었거나 실패했습니다.\n사유: " + reason;

                eventPublisher.publishEvent(new ChatSystemEvent(
                        order.getChatRoom().getId(),
                        MessageType.PAYMENT_FAILED,
                        customMessage,
                        order.getId()
                ));
        }

        // PG(토스 등) 승인 이후 공통 처리: PENDING -> PAID, 저장, 결제완료 시스템 메시지
        private OrderResponse markPaid(Order order) {
                Long orderId = order.getId();

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

                String customMessage = MessageType.PAYMENT_COMPLETED.getTemplate();

                eventPublisher.publishEvent(new ChatSystemEvent(
                        savedOrder.getChatRoom().getId(),
                        MessageType.PAYMENT_COMPLETED,
                        customMessage,
                        savedOrder.getId()
                ));

                return OrderResponse.from(savedOrder);
        }
}
