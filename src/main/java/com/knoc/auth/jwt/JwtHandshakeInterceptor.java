package com.knoc.auth.jwt;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {
    /** WebSocket 핸드셰이크 인터셉터
     * 연결 수립 전 쿠키에서 JWT를 추출해 검증하고,
     * 인증 정보를 attributes에 저장하여 Principal로 사용할 수 있게 함
     * 검증 실패 시 false 반환 → 연결 거부
     */

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        // 쿠키 -> JWT검증 -> 인증정보 저장
        if (request instanceof ServletServerHttpRequest servletRequest){
            HttpServletRequest httpRequest = servletRequest.getServletRequest();
            String accessToken = null;
            Cookie[] cookies = httpRequest.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("accessToken".equals(cookie.getName())){
                        accessToken = cookie.getValue();
                        break;
                    }
                }
            }

            if(jwtTokenProvider.validateToken(accessToken)){
                String email = jwtTokenProvider.getEmailFromToken(accessToken);

                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                attributes.put("principal", authentication);

                return true;
            }
        }

        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, @Nullable Exception exception) {
        // 핸드셰이크 완료 후 별도 처리 없음
    }
}
