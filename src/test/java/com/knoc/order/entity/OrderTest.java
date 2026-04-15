package com.knoc.order.entity;

import com.knoc.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    @Test
    @DisplayName("주문 상태는 PENDING에서 PAID로 변경 가능하다")
    void updateStatus_success() {
        // given
        Order order = Order.builder().build(); // 기본 PENDING

        // when
        order.updateStatus(OrderStatus.PAID);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("취소된 주문을 결제 완료 상태로 바꿀 수 없으며, 예외가 발생한다")
    void updateStatus_fail() {
        // given
        Order order = Order.builder().build();
        order.updateStatus(OrderStatus.CANCELLED);

        // when & then
        assertThatThrownBy(() -> order.updateStatus(OrderStatus.PAID))
                .isInstanceOf(BusinessException.class);
    }
}