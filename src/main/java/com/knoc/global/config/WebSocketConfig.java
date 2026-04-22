package com.knoc.global.config; // 1. 팀원의 글로벌 패키지 위치로 통일합니다.

import com.knoc.auth.jwt.JwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** STOMP 브로커 설정 파일
     * /ws 엔드포인트로 WebSocket 연결 진입점 등록
     * /app 클라이언트 발행 prefix (@MessageMapping 으로 라우팅)
     * /queue, /topic 브로커 구독 prefix ( queue는 1:1, topic는 1:다 )
     * ( /topic은 현재는 실제로 안쓰고 있습니다 )
     * /user 특정 유저에게 개인 메시지 라우팅 prefix
     *
     * JwtHandShakeInterceptor : WebSocket 핸드셰이크 시 요청 가로채서 JWT 인증 처리
     */
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(jwtHandshakeInterceptor)
                .withSockJS();
    }
}