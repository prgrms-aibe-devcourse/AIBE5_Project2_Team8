package com.knoc.order.service;

import com.knoc.chat.entity.ChatMessage;
import com.knoc.chat.entity.ChatRoom;
import com.knoc.chat.entity.MessageType;
import com.knoc.chat.repository.ChatMessageRepository;
import com.knoc.chat.repository.ChatRoomRepository;
import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import com.knoc.global.lock.MysqlNamedLockRepository;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import com.knoc.order.dto.OrderRequest;
import com.knoc.order.dto.OrderResponse;
import com.knoc.order.entity.Order;
import com.knoc.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MemberRepository memberRepository;
    private final MysqlNamedLockRepository namedLockRepository;

    public OrderResponse createOrderRequest(OrderRequest dto, Long seniorId, String idempotencyKey) {
        // 멱등 키는 "같은 요청을 여러 번 보내도 같은 결과"를 만들기 위한 식별자다.
        // 비어있으면 멱등 보장을 할 수 없으므로 요청 자체를 거절한다.
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // idempotencyKey로 orderNumber를 결정해야 재시도/중복요청이 동일 주문으로 귀결된다.
        String orderNumber = "ORD-" + idempotencyKey;

        // MySQL Named Lock은 "주문번호 단위 임계영역"을 만들어
        // 동시에 들어온 요청이 결제(또는 주문 생성) 로직을 중복 수행하지 못하게 막는다.
        String lockKey = "pay:" + orderNumber;

        boolean locked = namedLockRepository.getLock(lockKey, 5);
        if (!locked) {
            // 다른 요청이 이미 처리 중(락 선점)인데, 일정 시간 내에 락을 못 잡았다는 의미.
            // 정책에 따라 409(처리중) 등으로 바꿀 수 있지만, 우선은 공통 INVALID로 처리한다.
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        try {
            // 1) 락 안에서 먼저 조회: 이미 주문이 있다면 그대로 반환(멱등 응답)
            return orderRepository.findByOrderNumber(orderNumber)
                    .map(OrderResponse::from)
                    .orElseGet(() -> {
                        // 2) 없으면 생성: 주문 생성에 필요한 엔티티 조회 및 권한 검증
                        ChatRoom chatRoom = chatRoomRepository.findById(dto.getChatRoomId())
                                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));
                        Member junior = memberRepository.findById(dto.getJuniorId())
                                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
                        Member senior = memberRepository.findById(seniorId) // 현재 로그인 사용자(시니어)
                                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

                        // 요청자가 해당 채팅방의 시니어가 맞는지 검증
                        if (!chatRoom.getSenior().getId().equals(seniorId)) {
                            throw new BusinessException(ErrorCode.NOT_SENIOR_IN_ROOM);
                        }

                        try {
                            // 주문 저장(UNIQUE(order_number)가 최후의 보루)
                            Order order = Order.builder()
                                    .orderNumber(orderNumber)
                                    .chatRoom(chatRoom)
                                    .junior(junior)
                                    .senior(senior)
                                    .amount(dto.getAmount())
                                    .build();

                            Order savedOrder = orderRepository.save(order);

                            // 결제 요청 시스템 메시지 생성/저장
                            String formattedAmount = String.format("%,d", dto.getAmount());
                            ChatMessage message = ChatMessage.builder()
                                    .chatRoom(chatRoom)
                                    .messageType(MessageType.PAYMENT_REQUESTED)
                                    .content("시니어님이 " + formattedAmount
                                            + "원 결제를 요청했습니다.\n결제를 완료하시면 상세 리뷰 요청서를 작성하실 수 있습니다.")
                                    .referenceId(savedOrder.getId()) // 주문 ID 연결
                                    .sender(null) // 시스템 메시지이므로 발신자는 null 또는 별도의 시스템 계정
                                    .build();

                            chatMessageRepository.save(message);

                            // 저장된 주문을 응답 DTO로 반환
                            return OrderResponse.from(savedOrder);
                        } catch (DataIntegrityViolationException e) { // 데이터베이스가 요구하는 데이터 규칙(무결성)이 깨졌을 때 던져지는 예외
                            // 매우 드물게(동시성/재시도 타이밍) UNIQUE 충돌이 나면,
                            // "이미 생성된 주문"을 조회해서 멱등 응답으로 돌려준다.
                            return orderRepository.findByOrderNumber(orderNumber)
                                    .map(OrderResponse::from)
                                    .orElseThrow(() -> e);
                        }
                    });
        } finally {
            // 예외가 발생해도 락은 반드시 해제되어야 다음 요청이 진행될 수 있다.
            namedLockRepository.releaseLock(lockKey);
        }

    }
}
