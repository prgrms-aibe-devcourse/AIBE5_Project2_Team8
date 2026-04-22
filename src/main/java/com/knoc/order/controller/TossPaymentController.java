package com.knoc.order.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knoc.global.exception.BusinessException;
import com.knoc.order.repository.OrderRepository;
import com.knoc.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.Map;

// 토스페이먼츠 테스트 결제:
// - 결제 인증 성공 리다이렉트 후, 서버에서 결제 승인(confirm) API를 호출합니다.
// - 시크릿 키는 서버에서만 사용합니다(브라우저로 내려가면 안 됨).
// - 실제 HTTP 호출은 `TossPaymentConfig#tossRestClient`에서 생성된 공용 빈을 사용합니다.
//   (baseUrl, 타임아웃, Basic 인증 헤더가 자동 구성되어 있음)
@Slf4j
@Controller
@RequestMapping("/orders/payment/toss")
@RequiredArgsConstructor
public class TossPaymentController {

    private final ObjectMapper objectMapper;
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final RestClient tossRestClient;

    // 시크릿 키 설정 여부 가드용으로만 사용 (실제 인증 헤더는 tossRestClient에서 자동 주입)
    @Value("${toss.payments.secret-key:}")
    private String secretKey;

    // Toss 결제창 종료 (브라우저 리다이렉트, ?paymentKey=&orderId=&amount=)
    @GetMapping("/success")
    @ResponseBody
    public ResponseEntity<Void> success(
            @RequestParam String paymentKey,
            @RequestParam String orderId,
            @RequestParam int amount) {
        // 다시 돌아갈 채팅창 URL
        String chatURL = redirectToChat(orderId);

        // 파라미터 최소 검증: 잘못된 값은 Toss API/DB 조회 전에 차단
        // amount가 0이어도 검증 실패인 이유: Toss Payments API는 0원 결제를 지원하지 않음
        if (!StringUtils.hasText(paymentKey) || !StringUtils.hasText(orderId) || amount <= 0) {
            log.warn("Toss success 파라미터 검증 실패: orderId={}, amount={}", orderId, amount);
            orderService.recordPaymentFailure(orderId, null);
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION, chatURL).build();
        }

        if (secretKey == null || secretKey.isBlank()) {
            orderService.recordPaymentFailure(orderId, "서버 설정 오류로 결제 결과를 처리하지 못했습니다.");
            // 토스 리다이렉트 착지 응답은 필요하지만, 결과 화면을 보여주지 않고 채팅방 시스템 메시지로 전달하기 때문에 302로 이동.
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION, chatURL).build();
        }

        // Toss confirm 호출 전 사전 금액 검증 (돈 묶이기 전에 차단) + 이미 PAID면 멱등 허용
        try {
            orderService.verifyPaymentAmount(orderId, amount);
        } catch (BusinessException e) {
            orderService.recordPaymentFailure(orderId, e.getMessage());
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION, chatURL).build();
        }

        Map<String, Object> body = new HashMap<>();
        body.put("paymentKey", paymentKey);
        body.put("orderId", orderId);
        body.put("amount", amount);

        try {
            String json = tossRestClient
                    .post()
                    .uri("/v1/payments/confirm") // <- 토스 서버로 승인 요청 (baseUrl은 빈에서 설정)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(json);

            long confirmedAmount = amount;
            if (root.hasNonNull("totalAmount")) {
                confirmedAmount = root.get("totalAmount").asLong();
            }

            // 성공 시: DB에 결제완료 반영 + 채팅 시스템 메시지 발송은 서비스에서 처리
            orderService.confirmPayment(orderId, confirmedAmount);

            // 토스 리다이렉트 착지 응답은 필요하지만, 결과 화면을 보여주지 않고 채팅방 시스템 메시지로 전달하기 때문에 302로 이동.
            return ResponseEntity.status(302)
                    .header(HttpHeaders.LOCATION, chatURL)
                    .build();
        } catch (RestClientResponseException e) {
            log.warn("Toss 결제 승인 실패 응답: orderId={}, body={}", orderId, e.getResponseBodyAsString());
            orderService.recordPaymentFailure(orderId, null); // null -> MessageType.PAYMENT_FAILED 기본 템플릿
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION, chatURL).build();
        } catch (BusinessException e) {
            // confirm은 성공했는데 DB 반영이 실패한 케이스도 채팅 메시지로 남김
            log.warn("결제 DB 반영 실패: orderId={}, code={}", orderId, e.getMessage());
            orderService.recordPaymentFailure(orderId, e.getMessage());
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION, chatURL).build();
        } catch (Exception e) {
            log.warn("Toss confirm 처리 중 예기치 못한 오류: orderId={}", orderId, e);
            orderService.recordPaymentFailure(orderId, null);
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION, chatURL).build();
        }
    }

    @GetMapping("/fail")
    @ResponseBody
    public ResponseEntity<Void> fail(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String message,
            @RequestParam(required = false) String orderId) {
        // 다시 돌아갈 채팅창 URL
        String chatURL = redirectToChat(orderId);

        // 실패/취소도 채팅에 시스템 메시지로 남김
        log.warn("Toss 결제 실패 콜백: orderId={}, code={}, message={}", orderId, code, message);
        orderService.recordPaymentFailure(orderId, null);

        // 토스 리다이렉트 착지 응답은 필요하지만, 결과 화면을 보여주지 않고 채팅방 시스템 메시지로 전달하기 때문에 302로 이동.
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, chatURL).build();
    }

    // Toss 콜백 후 돌아갈 채팅방 URL 계산 (주문 조회 실패 시 "/"으로 폴백)
    private String redirectToChat(String tossOrderId) {
        if (!StringUtils.hasText(tossOrderId)) {
            return "/";
        }
        return orderRepository.findByOrderNumber(tossOrderId)
                .map(o -> "/chat/" + o.getChatRoom().getId())
                .orElse("/");
    }
}
