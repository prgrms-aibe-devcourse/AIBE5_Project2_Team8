package com.knoc.order.service;

import com.knoc.chat.entity.ChatRoom;
import com.knoc.chat.entity.ChatSystemEvent;
import com.knoc.chat.entity.MessageType;
import com.knoc.chat.repository.ChatMessageRepository;
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
import com.knoc.senior.entity.SeniorProfile;
import com.knoc.senior.repository.SeniorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Optional;

// 주문/결제 서비스
// 메서드 순서는 실제 호출 흐름(해피 패스)을 따른다:
//   1) createOrderRequest     - 시니어가 주문 생성
//   2) preparePayment         - 주니어가 결제 버튼 클릭 시 사전 검증/조회
//   3) verifyPaymentAmount    - Toss confirm API 호출 전 사전 금액 검증
//   4) confirmPayment         - Toss confirm 성공 후 상태 전이 (PENDING -> PAID)
//   5) markPaid (private)     - confirmPayment 내부 공통 처리 (저장 + 완료 이벤트)
//   6) recordPaymentFailure   - 실패/취소 경로
//
@Slf4j
@Service
@Transactional(readOnly = true) // 클래스 기본값: 읽기 전용 (쓰기 메서드에만 @Transactional 명시)
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ChatMessageRepository chatMessageRepository;

    // 결제 실패 메시지 중복 발행 방지를 위한 쿨다운 (30초)
    private static final long FAILURE_MESSAGE_COOLDOWN_SECONDS = 30L;
    private final SeniorProfileRepository seniorProfileRepository;

    @Transactional
    public OrderResponse createOrderRequest(OrderRequest dto, Long seniorId, String idempotencyKey) {
        // 0. 멱등키 검증 (비어있거나 너무 짧거나 너무 긴 경우에 대한 에러 처리)
        // Toss 제약: orderId 6~64자
        // idempotencyKey가 60자 초과면 orderNumber가 64자 초과이므로 에러.
        if (!StringUtils.hasText(idempotencyKey)
                || idempotencyKey.trim().length() < 10
                || idempotencyKey.trim().length() > 60) { // "ORD-" prefix 4자 여유
            throw new BusinessException(ErrorCode.INVALID_IDEMPOTENCY_KEY);
        }

        // 1. 멱등키를 기반으로 주문 번호 생성
        String orderNumber = "ORD-" + idempotencyKey;

        // 2. 멱등성 체크: 이미 해당 키로 생성된 주문이 있는지 확인
        return orderRepository.findByOrderNumber(orderNumber)
                .map(OrderResponse::from) // 있다면 바로 반환
                .orElseGet(() -> { // 없다면 주문 객체 생성, 시스템 이벤트 발행
                    // 3. 엔티티 조회 및 검증 (채팅방, 주니어, 시니어)
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

                    // 4. 주문 객체 생성 및 DB 저장
                    Order order = Order.builder()
                            .orderNumber(orderNumber)
                            .chatRoom(chatRoom)
                            .junior(junior)
                            .senior(senior)
                            .amount(dto.getAmount())
                            .build();

                    Order savedOrder;
                    try { // saveAndFlush를 통해 즉시 쿼리를 날려 충돌을 감지함
                        savedOrder = orderRepository.saveAndFlush(order);
                    } catch (DataIntegrityViolationException e) { // 데이터 무결성 제약 조건을 위반했을 때 발생하는 예외
                        // 동시 요청으로 인해 UNIQUE 제약조건 위반 시, 이미 저장된 주문을 다시 찾아 반환
                        return orderRepository.findByOrderNumber(orderNumber)
                                .map(OrderResponse::from)
                                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
                    }

                    // 5. 결제 요청 메시지 생성 및 시스템 이벤트 발행
                    String customMessage = MessageType.PAYMENT_REQUESTED.formatMessage(dto.getAmount());
                    eventPublisher.publishEvent(new ChatSystemEvent(
                            chatRoom.getId(),
                            MessageType.PAYMENT_REQUESTED,
                            customMessage,
                            savedOrder.getId()
                    ));
                    // 6. 저장된 주문을 클라이언트에게 보여줄 전용 응답 객체(DTO)로 변환
                    return OrderResponse.from(savedOrder);
                });
    }

    // 결제창 호출 전 단계(사전 검증/조회)
    // 실제 결제 승인 후 처리는 confirmPayment(String, long) 메서드에서 수행
    public OrderResponse preparePayment(Long orderId, Long juniorId) {
        // 입력 검증
        if (juniorId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        // 주니어 검증
        if (order.getJunior() == null || order.getJunior().getId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
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

        // 시니어 직군 가져오기
        String seniorPosition = seniorProfileRepository.findByMemberId(order.getSenior().getId())
                .map(SeniorProfile::getPosition)
                .orElse(null);

        return OrderResponse.from(order, seniorPosition); // 결제 가능한 상태(PENDING)면 dto 만들어서 반환
    }

    // Toss confirm API 호출 전 사전 금액 검증.
    // "돈이 Toss에 묶이기 전에" 주문 금액과 요청 금액의 일치 여부를 확인하여,
    // 승인 이후에야 불일치를 발견하는 치명 케이스(환불 필요 상태)를 원천 차단하는 것이 목적.
    // - 로컬에 주문이 없는 경우(예: 메인 테스트용 TEST-...): confirmPayment 정책과 동일하게 스킵
    // - 이미 PAID: 중복 호출 대비 멱등 허용
    // - 불일치: ORDER_PAYMENT_AMOUNT_MISMATCH 예외 + error 로그
    // NOTE: confirmPayment에도 동일 성격의 사후 재검증이 남아있음. 두 검증은 defense-in-depth로 공존함
    // (사전: 일반적인 변조/버그 차단 / 사후: Toss 응답 totalAmount가 요청 amount와 달라지는 극희소 케이스 대비)
    public void verifyPaymentAmount(String tossOrderId, int amount) {
        // 음수 방어 (쿼리 변조로 음수 전달 가능)
        if (amount < 0) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_AMOUNT);
        }

        // OrderId가 없거나 빈 값이면 에러
        if (!StringUtils.hasText(tossOrderId)) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_ORDER_NUMBER);
        }

        Optional<Order> found = orderRepository.findByOrderNumber(tossOrderId);
        if (found.isEmpty()) {
            return; // 로컬에 없는 주문은 스킵 (confirmPayment 정책과 동일)
        }
        Order order = found.get();

        // 이미 PAID면 멱등 허용 (중복 confirm 호출 대비)
        if (order.getStatus() == OrderStatus.PAID) {
            return;
        }

        if (order.getAmount() != amount) {
            log.error("결제 사전 금액 불일치: orderId={}, orderNumber={}, expected={}, received={}",
                    order.getId(), order.getOrderNumber(), order.getAmount(), amount);
            throw new BusinessException(ErrorCode.ORDER_PAYMENT_AMOUNT_MISMATCH);
        }
    }

    // 토스페이먼츠 결제 승인(confirm) 성공 후 호출
    // tossOrderId는 결제 요청 시 넣은 주문번호로, OrderService의 orderNumber와 같아야 함
    // 로컬에 해당 주문이 없으면(예: 메인 테스트용 TEST-...) Optional.empty() 를 반환
    // NOTE: verifyPaymentAmount에서 이미 동일한 입력/조회 체크가 수행될 수 있지만,
    // confirmPayment도 단독 호출 가능한 public 엔트리포인트이므로 방어적으로 재검증/재조회를 수행함
    // (defense-in-depth, 쿼리 비용은 UNIQUE 인덱스 조회라 무시 가능)
    @Transactional
    public Optional<OrderResponse> confirmPayment(String tossOrderId, long confirmedAmount) {
        if (!StringUtils.hasText(tossOrderId)) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_ORDER_NUMBER);
        }
        Optional<Order> found = orderRepository.findByOrderNumber(tossOrderId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Order order = found.get();

        // 이미 PAID면 금액검증/마크 없이 멱등 반환
        if (order.getStatus() == OrderStatus.PAID) {
            return Optional.of(OrderResponse.from(order));
        } // SETTLED, CANCELLED 상태는 결제 불가능한 상태이므로 에러
        else if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.ORDER_CANNOT_BE_PAID);
        }

        // 이 분기는:
        //   (a) Toss 응답 totalAmount가 우리가 보낸 amount와 다른 극희소 케이스
        //   (b) confirmPayment가 다른 경로에서 단독 호출된 경우
        // 를 잡아내기 위한 최후 방어선. 도달 시 에러 로그 + 예외로 상위에 알림.
        //
        // 본 프로젝트는 Toss 테스트 키로만 동작하므로 실제 환불 API 호출은 불필요함
        // 운영 전환 시에는 이 분기에서 돈이 묶이는 상황이므로
        // POST https://api.tosspayments.com/v1/payments/{paymentKey}/cancel 연동 필요함
        if (order.getAmount() != confirmedAmount) {
            log.error("결제 사후 금액 불일치(승인 완료 상태): orderId={}, orderNumber={}, expected={}, confirmed={}",
                    order.getId(), order.getOrderNumber(), order.getAmount(), confirmedAmount);
            throw new BusinessException(ErrorCode.ORDER_PAYMENT_AMOUNT_MISMATCH);
        }
        return Optional.of(markPaid(order)); // 결제 완료 처리
    }

    // PG(토스 등) 승인 이후 공통 처리: PENDING -> PAID, 저장, 결제완료 시스템 메시지
    private OrderResponse markPaid(Order order) {
        Long orderId = order.getId();
        order.updateStatus(OrderStatus.PAID);

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

        // 결제 완료 시스템 이벤트 발행
        eventPublisher.publishEvent(new ChatSystemEvent(
                savedOrder.getChatRoom().getId(),
                MessageType.PAYMENT_COMPLETED,
                null, // 템플릿 사용 → "결제가 성공적으로 처리되었습니다.\n결제 금액은 구매 확정 시까지 Knoc에서 안전하게 보호합니다."
                savedOrder.getId()
        ));

        // 주니어 전용: 결제 완료 직후 상세 리뷰 요청서 작성 안내
        // - 결제 성공 후 success 리다이렉트로 페이지가 새로 로드될 수 있으므로 DB에도 저장한다.
        // - 단, 시니어에게는 전송하지 않으며(실시간/히스토리 모두), 시니어 화면에서는 서버에서 메시지를 필터링한다.
        eventPublisher.publishEvent(new ChatSystemEvent(
                savedOrder.getChatRoom().getId(),
                MessageType.REVIEW_REQUESTED,
                null,
                savedOrder.getId(),
                true,   // sendToJunior
                false   // sendToSenior
        ));

        return OrderResponse.from(savedOrder);
    }

    // 주니어가 구매 확정 → PAID → SETTLED
    @Transactional
    public void settleOrder(Long orderId, Long juniorId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getJunior().getId().equals(juniorId)) {
            throw new BusinessException(ErrorCode.NOT_JUNIOR_FOR_ORDER);
        }
        if (order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(ErrorCode.ORDER_CANNOT_BE_PAID);
        }

        order.updateStatus(OrderStatus.SETTLED);
        orderRepository.save(order);
        order.getChatRoom().close();

        eventPublisher.publishEvent(new ChatSystemEvent(
                order.getChatRoom().getId(),
                MessageType.PURCHASE_CONFIRMED,
                null,
                order.getId()
        ));
        eventPublisher.publishEvent(new ChatSystemEvent(
                order.getChatRoom().getId(),
                MessageType.ROOM_CLOSE,
                MessageType.ROOM_CLOSE.getTemplate(),
                null
        ));
    }

    // 토스 결제 실패/취소 리다이렉트 후 호출
    // 주문이 존재하면 채팅방에 시스템 메시지를 저장함
    // 주문이 없으면(예: TEST-...) 아무 것도 하지 않음
    // 주문 상태는 기본적으로 변경하지 않음(PENDING 유지)
    @Transactional
    public void recordPaymentFailure(String tossOrderId, String reason) {
        if (!StringUtils.hasText(tossOrderId)) {
            return; // 토스 fail 콜백에서 orderId가 없을 수 있어 조용히 무시
        }
        Optional<Order> found = orderRepository.findByOrderNumber(tossOrderId);
        if (found.isEmpty()) {
            return;
        }
        Order order = found.get();

        // 방어선1: 이미 결제 성공/정산된 주문이면 실패 메시지 발행 안 함
        // (결제 성공 직후 뒤늦게 fail 콜백이 도달하는 엣지 케이스 방어)
        if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.SETTLED) {
            return;
        }

        // 방어선2: 최근 30초 이내 동일 주문의 실패 메시지가 이미 있으면 스킵
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(FAILURE_MESSAGE_COOLDOWN_SECONDS);
        if (chatMessageRepository.existsByReferenceIdAndMessageTypeAndCreatedAtAfter(
                order.getId(), MessageType.PAYMENT_FAILED, threshold)) {
            return;
        }

        // 채팅방에 실패 메시지가 실제로 발행되는 시점의 정상 플로우 로그
        // (에러 발생 자체는 호출부 컨트롤러에서 WARN으로 별도 기록됨)
        log.info("PAYMENT_FAILED 이벤트 발행: orderId={}, reason={}", order.getId(), reason);

        // 결제 실패 시스템 이벤트 발행
        eventPublisher.publishEvent(new ChatSystemEvent(
                order.getChatRoom().getId(),
                MessageType.PAYMENT_FAILED,
                null, // 템플릿 사용 → "결제가 실패하거나 취소되었습니다. 다시 시도해주세요."
                order.getId()
        ));
    }
}