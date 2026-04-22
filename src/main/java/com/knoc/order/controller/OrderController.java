package com.knoc.order.controller;

import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import com.knoc.order.dto.OrderRequest;
import com.knoc.order.dto.OrderResponse;
import com.knoc.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Tag(name="Order-Controller",description = "멘토링 주문 및 결제 관리 API")
@RestController // JSON 데이터를 주고받는 API 전용 컨트롤러
@RequiredArgsConstructor
@RequestMapping("/orders")
public class OrderController {
    private final OrderService orderService;
    private final MemberRepository memberRepository;

    @Operation(summary = "멘토링 주문 요청", description = "시니어가 멘토링 주문을 생성합니다. 요청 중복을 방지하기 위한 Idempotency-Key가 필요합니다.")
    @PostMapping("/request")
    @PreAuthorize("hasRole('SENIOR')") // 시니어만 결제 요청 가능
    public ResponseEntity<OrderResponse> requestOrder(@AuthenticationPrincipal UserDetails userDetails,
                                                      @RequestBody OrderRequest dto,
                                                      @RequestHeader("Idempotency-Key") String idempotencyKey) {
        // 1. 현재 로그인한 시니어 ID를 가져온다.
        Member member = memberRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Long seniorId = member.getId();

        // 2. 서비스를 호출하여 주문을 생성하고 응답 DTO를 받는다.
        OrderResponse orderResponse = orderService.createOrderRequest(dto, seniorId, idempotencyKey);

        // 3. 생성된 주문 정보(JSON 데이터로 변환됨)와 함께 200 OK 응답을 브라우저로 보낸다.
        return ResponseEntity.ok(orderResponse);
    }

    @Operation(summary = "멘토링 결제 준비", description = "주니어가 주문ID를 바탕으로 결제를 준비합니다.")
    @PostMapping("/{orderId}/pay")
    @PreAuthorize("hasRole('USER')")  // 주니어 결제 가능
    public ResponseEntity<OrderResponse> requestPay(@AuthenticationPrincipal UserDetails userDetails,
                                                    @PathVariable Long orderId) {
        Member member = memberRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Long juniorId = member.getId();
        OrderResponse orderResponse = orderService.preparePayment(orderId, juniorId);
        return ResponseEntity.ok(orderResponse);
    }
}
