package com.knoc.order.controller;

import com.knoc.order.dto.OrderRequest;
import com.knoc.order.dto.OrderResponse;
import com.knoc.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController // JSON 데이터를 주고받는 API 전용 컨트롤러
@RequiredArgsConstructor
@RequestMapping("/orders")
public class OrderController {
    private final OrderService orderService;

    @PostMapping("/request")
    @PreAuthorize("hasRole('SENIOR')") // 시니어만 결제 요청 가능
    public ResponseEntity<OrderResponse> requestOrder(@RequestBody OrderRequest dto) {
        // 1. 현재 로그인한 시니어 ID를 가져온다.
        Long seniorId = 1L; // 테스트용 id

        // 2. 서비스를 호출하여 주문을 생성하고 응답 DTO를 받는다.
        OrderResponse orderResponse = orderService.createOrderRequest(dto, seniorId);

        // 3. 생성된 주문 정보(JSON 데이터로 변환됨)와 함께 200 OK 응답을 브라우저로 보낸다.
        return ResponseEntity.ok(orderResponse);
    }

    @PostMapping("/{orderId}/pay")
    @PreAuthorize("hasRole('USER')")  // 주니어 결제 가능
    public ResponseEntity<OrderResponse> requestPay(@PathVariable Long orderId, @RequestHeader("Idempotency-Key") String idempotencyKey) {
        Long juniorId = 1L; // 테스트용 id
        OrderResponse orderResponse = orderService.payOrder(orderId, idempotencyKey, juniorId);
        return ResponseEntity.ok(orderResponse);
    }
}
