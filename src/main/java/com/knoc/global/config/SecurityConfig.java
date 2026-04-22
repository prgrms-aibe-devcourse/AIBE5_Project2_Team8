package com.knoc.global.config;

import com.knoc.auth.jwt.JwtAuthenticationFilter;
import com.knoc.auth.jwt.JwtTokenProvider;
import com.knoc.auth.jwt.LoginSuccessHandler;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    // Spring 컨테이너에 BCryptPasswordEncoder를 Bean으로 등록
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 의존성 주입
    private final UserDetailsService userDetailsService;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginSuccessHandler loginSuccessHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // 세션을 사용하지 않음(jwt 중심)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // url 접근 권한 설정
                .authorizeHttpRequests(auth -> auth
                        // [공통/비로그인] AUTH-01(로그인), POST-02(후기 목록 조회-비로그인 가능 명시)
                        .requestMatchers("/", "/auth/**", "/error/**", "/reviews/posts", "/orders/payment/toss/**", "/css/**", "/js/**", "/images/**", "/ws/**").permitAll()
                        // [시니어 전용] PROF-01(프로필 관리), REV-02(리포트 제출), MY-02(시니어 마이페이지), ORD-01(결제 요청)
                        .requestMatchers("/senior/profile/**", "/reports/**", "/my/senior/**", "/orders/request").hasRole("SENIOR")
                        // [주니어 전용] CHAT-01(방생성), ORD-01/02(결제), REV-01(요청), SET-01(구매확정), POST-01(후기작성), MY-01(주니어 마이페이지)
                        .requestMatchers("/chats/new", "/orders/**", "/requests/**", "/settlements/confirm", "/reviews/posts/write", "/my/junior/**").hasRole("USER")
                        // [공통 로그인] /my/** 등 역할 무관 인증 필요 경로
                        .requestMatchers("/my/**").authenticated()
                        // 그외 나머지 경로는 로그인 사용자 허용
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.sendError(HttpServletResponse.SC_FORBIDDEN, "접근 권한이 없습니다.");
                        })
                )

                // 로그인 폼 설정 업데이트
                .formLogin(form -> form
                        .loginPage("/auth/login")   // GET: login.html 파일을 보여줌
                        .loginProcessingUrl("/auth/login")   // POST: 화면에서 폼을 제출하면 Spring Security가 가로채서 로그인 처리
                        .successHandler(loginSuccessHandler)  // 성공 시 jwt 토큰 발급
                        .failureUrl("/auth/login?error=true")   // 로그인 실페 시 파라미터 달고 다시 로그인 화면으로
                        .permitAll()
                )

                // 모든 요청 앞에 jwt 필터 배치
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService),
                        UsernamePasswordAuthenticationFilter.class)

                .logout(logout -> logout
                        .logoutUrl("/auth/logout")          // POST 요청을 받을 URL
                        .logoutSuccessUrl("/")              // 로그아웃 성공 후 이동
                        .deleteCookies("accessToken", "refreshToken")  // 쿠키 삭제
                        .permitAll()
                );

        return http.build();

    }
}