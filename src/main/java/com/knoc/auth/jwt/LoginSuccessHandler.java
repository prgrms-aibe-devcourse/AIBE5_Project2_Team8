package com.knoc.auth.jwt;

import com.knoc.member.MemberRole;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
        throws IOException {

        String email = authentication.getName();
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst().orElse("ROLE_" + MemberRole.USER.name());

        // 쿠키 2개 생성
        String accessToken = jwtTokenProvider.createAccessToken(email, role);
        String refreshToken = jwtTokenProvider.createRefreshToken(email);

        // access token
        Cookie accessCookie = new Cookie("accessToken", accessToken);
        accessCookie.setPath("/");  // 모든 페이지에서 사용
        accessCookie.setHttpOnly(true);  // 자바스크립트 공격 방지
        accessCookie.setMaxAge(60 * 30);
        response.addCookie(accessCookie);

        // refresh token
        Cookie refreshCookie = new Cookie("refreshToken", refreshToken);
        refreshCookie.setPath("/");
        refreshCookie.setHttpOnly(true);
        refreshCookie.setMaxAge(60 * 60 * 24 * 7);
        response.addCookie(refreshCookie);

        response.sendRedirect("/");  // 로그인 성공하여 메인화면으로

    }
}
