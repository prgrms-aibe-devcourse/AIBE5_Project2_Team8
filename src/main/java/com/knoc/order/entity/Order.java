package com.knoc.order.entity;

import com.knoc.chat.entity.ChatRoom;
import com.knoc.global.entity.BaseEntity;
import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import com.knoc.member.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "orders") // // 예약어 충돌 방지를 위해 테이블명 명시
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 64)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "junior_id", nullable = false)
    private Member junior;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "senior_id", nullable = false)
    private Member senior;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Version // // 낙관적 락을 위한 버전 관리 (버전 정보를 활용해 동시성 제어함)
    @Column(nullable = false)
    private Long version;

    @Builder
    public Order(String orderNumber, ChatRoom chatRoom, Member junior, Member senior, int amount) {
        this.orderNumber = orderNumber;
        this.chatRoom = chatRoom;
        this.junior = junior;
        this.senior = senior;
        this.amount = amount;
        this.status = OrderStatus.PENDING;
    }

    // 상태 변경 가능 여부 확인 및 상태 전환
    public void updateStatus(OrderStatus toStatus) {
        validateTransition(this.status, toStatus);
        this.status = toStatus;
    }

    private void validateTransition(OrderStatus from, OrderStatus to) {
        boolean isPossible = false;

        switch (from) {
            case PENDING -> // 결제 대기 중에는 결재 완료 또는 취소만 가능
                isPossible = (to == OrderStatus.PAID || to == OrderStatus.CANCELLED);
            case PAID -> // 결재 완료 중에는 정산 완료 또는 취소만 가능
                isPossible = (to == OrderStatus.SETTLED || to == OrderStatus.CANCELLED);
            // SETTLED, CANCELLED(정산 완료, 취소)에서는 변경 불가능 -> isPossible = false
        }

        if (!isPossible)
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }
}