package com.knoc.order.entity;

import com.knoc.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    // ============================
    // 상태 전이 성공 테스트
    // ============================
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
    @DisplayName("주문 상태는 PENDING에서 CANCELLED로 변경 가능하다")
    void updateStatus_pendingToCancelled() {
        // given
        Order order = Order.builder().build(); // 기본 PENDING

        // when
        order.updateStatus(OrderStatus.CANCELLED);

        // when
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("주문 상태는 PAID에서 SETTLED로 변경 가능하다")
    void updateStatus_paidToSettled() {
        // given
        Order order = Order.builder().build(); // 기본 PENDING
        order.updateStatus(OrderStatus.PAID);

        // when
        order.updateStatus(OrderStatus.SETTLED);

        // when
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SETTLED);
    }

    @Test
    @DisplayName("주문 상태는 PAID에서 CANCELLED로 변경 가능하다")
    void updateStatus_paidToCancelled() {
        // given
        Order order = Order.builder().build(); // 기본 PENDING
        order.updateStatus(OrderStatus.PAID);

        // when
        order.updateStatus(OrderStatus.CANCELLED);

        // when
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    // ============================
    // 상태 전이 불가 테스트
    // ============================
    @Test
    @DisplayName("CANCELLED 주문은 PAID로 바꿀 수 없으며, 예외가 발생한다")
    void updateStatus_fail() {
        // given
        Order order = Order.builder().build();
        order.updateStatus(OrderStatus.CANCELLED);

        // when & then
        assertThatThrownBy(() -> order.updateStatus(OrderStatus.PAID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("PENDING 주문은 SETTLED로 건너뛸 수 없으며, 예외가 발생한다")
    void updateStatus_fail_pendingToSettled() {
        // given
        Order order = Order.builder().build();

        // when & then
        assertThatThrownBy(() -> order.updateStatus(OrderStatus.SETTLED))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("PAID 주문은 PENDING으로 되돌릴 수 없으며, 예외가 발생한다")
    void updateStatus_fail_paidToPending() {
        // given
        Order order = Order.builder().build();
        order.updateStatus(OrderStatus.PAID); // PENDING → PAID
        // when & then
        assertThatThrownBy(() -> order.updateStatus(OrderStatus.PENDING))
                .isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING", "PAID", "CANCELLED"})
    @DisplayName("SETTLED 주문은 어떤 상태로도 변경할 수 없으며, 예외가 발생한다")
    void updateStatus_fail_fromSettled(OrderStatus to) {
        // given
        Order order = Order.builder().build();
        order.updateStatus(OrderStatus.PAID);
        order.updateStatus(OrderStatus.SETTLED);
        
        // when & then
        assertThatThrownBy(() -> order.updateStatus(to))
                .isInstanceOf(BusinessException.class);
    }
}