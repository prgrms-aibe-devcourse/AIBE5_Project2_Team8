package com.knoc.global.config;

import com.knoc.auth.jwt.JwtAuthenticationFilter;
import com.knoc.auth.jwt.JwtTokenProvider;
import com.knoc.auth.jwt.LoginSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    // Spring 컨테이너에 BCryptPasswordEncoder를 Bean으로 등록
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, LoginSuccessHandler loginSuccessHandler,
                                           JwtTokenProvider jwtTokenProvider, UserDetailsService userDetailsService) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // 세션을 사용하지 않음(jwt 중심)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // url 접근 권한 설정
                .authorizeHttpRequests(auth -> auth
                        // auth로 시작하는 모든 url 인증 없이 접근 허용(정적 리소스 추가)
                        .requestMatchers("/auth/**", "/error/**", "/css/**", "/js/**", "/images/**").permitAll()
                        // 그외 나머지 경로는 인증을 해야만 접근 허용
                        .anyRequest().authenticated()
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
                        .deleteCookies("accessToken")  // 쿠키 삭제
                        .permitAll()
                );

        return http.build();

    }
}
