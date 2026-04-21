package com.knoc.order.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knoc.global.exception.BusinessException;
import com.knoc.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

// 토스페이먼츠 테스트 결제:
// - 결제 인증 성공 리다이렉트 후, 서버에서 결제 승인(confirm) API를 호출합니다.
// - 시크릿 키는 서버에서만 사용합니다(브라우저로 내려가면 안 됨).
@Controller
@RequestMapping("/orders/payment/toss")
@RequiredArgsConstructor
public class TossPaymentController {

    private final ObjectMapper objectMapper;
    private final OrderService orderService;

    @Value("${toss.payments.secret-key:}")
    private String secretKey;

    @GetMapping("/success")
    @ResponseBody
    public ResponseEntity<Void> success(
            @RequestParam String paymentKey,
            @RequestParam String orderId,
            @RequestParam long amount) {
        if (secretKey == null || secretKey.isBlank()) {
            orderService.recordPaymentFailure(orderId, "서버 설정 오류로 결제 결과를 처리하지 못했습니다.");
            // 토스 리다이렉트 착지 응답은 필요하지만, 결과 화면을 보여주지 않고 채팅방 시스템 메시지로 전달하기 때문에 302로 이동.
            // (채팅 URL이 정해지면 여기 Location("/")만 채팅방 URL로 바꾸면 됨)
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION, "/").build();
        }

        // Basic 인증: {secretKey}: 를 Base64 인코딩
        String authHeader = "Basic " + Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));

        Map<String, Object> body = new HashMap<>();
        body.put("paymentKey", paymentKey);
        body.put("orderId", orderId);
        body.put("amount", amount);

        try {
            String json = RestClient.create()
                    .post()
                    .uri("https://api.tosspayments.com/v1/payments/confirm") // <- 토스 서버로 승인 요청
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
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
            // (채팅 URL이 정해지면 여기 Location("/")만 채팅방 URL로 바꾸면 됨)
            return ResponseEntity.status(302)
                    .header(HttpHeaders.LOCATION, "/")
                    .build();
        } catch (RestClientResponseException e) {
            orderService.recordPaymentFailure(orderId,
                    parseTossErrorMessage(e.getResponseBodyAsString()));
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION, "/").build();
        } catch (BusinessException e) {
            // confirm은 성공했는데 DB 반영이 실패한 케이스도 채팅 메시지로 남김
            orderService.recordPaymentFailure(orderId, e.getMessage());
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION, "/").build();
        } catch (Exception e) {
            orderService.recordPaymentFailure(orderId, e.getMessage());
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION, "/").build();
        }
    }

    @GetMapping("/fail")
    @ResponseBody
    public ResponseEntity<Void> fail(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String message,
            @RequestParam(required = false) String orderId) {
        String reason = (message == null || message.isBlank()) ? null : message;
        if (code != null && !code.isBlank()) {
            reason = (reason == null) ? code : reason + " (" + code + ")";
        }
        // 실패/취소도 채팅에 시스템 메시지로 남김
        orderService.recordPaymentFailure(orderId, reason);

        // 토스 리다이렉트 착지 응답은 필요하지만, 결과 화면을 보여주지 않고 채팅방 시스템 메시지로 전달하기 때문에 302로 이동.
        // (채팅 URL이 정해지면 여기 Location("/")만 채팅방 URL로 바꾸면 됨)
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, "/").build();
    }

    private String parseTossErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "결제 승인 요청이 거절되었습니다.";
        }
        try {
            JsonNode n = objectMapper.readTree(responseBody);
            String msg = n.path("message").asText(null);
            if (msg != null && !msg.isBlank()) {
                return msg;
            }
        } catch (Exception ignored) {
            // 파싱 실패 시 원문 반환
        }
        return responseBody;
    }
}
