package com.knoc.dashboard;

import com.knoc.order.entity.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
public class JuniorDashboardDto {

    private final Long id;
    private final String nickname;
    private final long pendingCount;
    private final long inProgressCount;
    private final long completedCount;
    private final List<OrderSummaryDto> orders;

    @Builder
    public JuniorDashboardDto(Long id, String nickname, long pendingCount, long inProgressCount,
                               long completedCount, List<OrderSummaryDto> orders) {
        this.id = id;
        this.nickname = nickname;
        this.pendingCount = pendingCount;
        this.inProgressCount = inProgressCount;
        this.completedCount = completedCount;
        this.orders = orders;
    }

    @Getter
    public static class OrderSummaryDto {

        private final Long orderId;
        private final String orderNumber;
        private final String seniorNickname;
        private final OrderStatus status;
        private final boolean hasReview;

        @Builder
        public OrderSummaryDto(Long orderId, String orderNumber, String seniorNickname,
                                OrderStatus status, boolean hasReview) {
            this.orderId = orderId;
            this.orderNumber = orderNumber;
            this.seniorNickname = seniorNickname;
            this.status = status;
            this.hasReview = hasReview;
        }
    }
}