package com.knoc.settlement.entity;

import com.knoc.member.Member;
import com.knoc.order.entity.Order;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 서비스 제공(주문)이 완료된 후, 서비스 제공자(Senior)에게 대금을 지급하기 위한 정보를 관리한다.
// 핵심 역할: 어떤 주문(Order)에 대해 어느 시니어(Member senior)에게 얼마(amount)를 정산해줘야 하는지를 기록.

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "settlement")
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "senior_id", nullable = false)
    private Member senior;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementStatus status;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Builder
    public Settlement(Order order, Member senior, int amount) {
        this.order = order;
        this.senior = senior;
        this.amount = amount;
        this.status = SettlementStatus.PENDING;
    }
}