package com.knoc.auth.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String accessToken = getCookieValue(request, "accessToken");
        String refreshToken = getCookieValue(request, "refreshToken");

        // access token 살아있으면 통과
        if(accessToken != null && jwtTokenProvider.validateToken(accessToken)) {
            setAuthentication(jwtTokenProvider.getEmailFromToken(accessToken));
        }
        // access token 만료, refresh token이 유효하면 access token 재발급
        else if(refreshToken != null && jwtTokenProvider.validateToken(refreshToken)) {
            String email = jwtTokenProvider.getEmailFromToken(refreshToken);
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            String role = userDetails.getAuthorities().iterator().next().getAuthority();

            String newAccessToken = jwtTokenProvider.createAccessToken(email, role);
            Cookie newCookie = new Cookie("accessToken", newAccessToken);
            newCookie.setPath("/");
            newCookie.setHttpOnly(true);
            newCookie.setMaxAge(60 * 30);
            response.addCookie(newCookie);

            setAuthentication(email);

        }
        filterChain.doFilter(request, response);

    }
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // /ws 로 시작하는 웹소켓 연결 요청은 HTTP 단계에서 토큰 검사를 하지 않도록 예외 처리
        return path.startsWith("/ws");
    }

    // 쿠키 배열에서 원하는 쿠키를 가져옴
    private String getCookieValue(HttpServletRequest request, String cookieName) {
        if(request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> c.getName().equals(cookieName))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);

    }

    private void setAuthentication(String email) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

}
