package com.knoc.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

// 토스페이먼츠 REST API 호출 전용 RestClient 설정
// - baseUrl: application.properties의 toss.payments.base-url 사용
// - connect/read 타임아웃: 토스가 느리거나 멈췄을 때 요청 스레드가 무한 블록되는 것을 방지
// - Basic 인증 헤더(시크릿 키): 빈 생성 시 1회 조립하여 defaultHeader로 자동 주입
//   → 컨트롤러에서 더 이상 헤더를 수동으로 만들 필요 없음
@Configuration
public class TossPaymentConfig {

    @Bean
    public RestClient tossRestClient(
            @Value("${toss.payments.base-url:https://api.tosspayments.com}") String baseUrl,
            @Value("${toss.payments.secret-key:}") String secretKey,
            @Value("${toss.payments.connect-timeout:3s}") Duration connectTimeout,
            @Value("${toss.payments.read-timeout:10s}") Duration readTimeout) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout); // Toss 서버와 TCP 연결 자체가 3초 내에 안 맺어지면 실패 처리
        factory.setReadTimeout(readTimeout); // Toss 서버에서 응답이 10초 내에 안 오면 실패 처리

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory);

        // 시크릿 키가 설정돼 있을 때만 인증 헤더 주입 (미설정 환경에서도 애플리케이션 기동은 허용)
        if (secretKey != null && !secretKey.isBlank()) {
            String authHeader = "Basic " + Base64.getEncoder()
                    .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, authHeader);
        }

        return builder.build();
    }
}
