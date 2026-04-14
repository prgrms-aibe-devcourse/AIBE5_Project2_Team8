package com.knoc.order.entity;

import com.knoc.chat.entity.ChatRoom;
import com.knoc.global.entity.BaseEntity;
import com.knoc.member.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "orders")
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

    @Version
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
}