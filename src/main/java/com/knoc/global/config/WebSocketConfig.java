package com.knoc.global.config; // 1. 팀원의 글로벌 패키지 위치로 통일합니다.

import com.knoc.auth.jwt.JwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
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
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                // ✅ CONNECT에만 한정하지 않고, 세션에 인증 객체가 있다면 항상 달아줍니다!
                if (accessor != null && accessor.getSessionAttributes() != null) {
                    Object principal = accessor.getSessionAttributes().get("principal");

                    if (principal instanceof java.security.Principal) {
                        // STOMP 세션의 공식 유저로 등록!
                        accessor.setUser((java.security.Principal) principal);
                    }
                }

                return message;
            }
        });
    }
}