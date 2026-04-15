package com.knoc.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    // Spring 컨테이너에 BCryptPasswordEncoder를 Bean으로 등록
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // url 접근 권한 설정
                .authorizeHttpRequests(auth -> auth
                        // auth로 시작하는 모든 url 인증 없이 접근 허용
                        .requestMatchers("/auth/**", "/error/**").permitAll()
                        // 그외 나머지 경로는 인증을 해야만 접근 허용
                        .anyRequest().authenticated()
                )

                // 로그인 폼 설정 업데이트
                .formLogin(form -> form
                        .loginPage("/auth/login")   // GET: login.html 파일을 보여줌
                        .loginProcessingUrl("/auth/login")   // POST: 화면에서 폼을 제출하면 Spring Security가 가로채서 로그인 처리
                        .defaultSuccessUrl("/")   // 로그인 성공 시 메인 화면으로
                        .failureUrl("/auth/login?error=true")   // 로그인 실페 시 파라미터 달고 다시 로그인 화면으로
                        .permitAll()
                )
                .rememberMe(remember -> remember
                        .key("knoc-secret-key")  // 쿠키를 암호화할 임의의 문자열
                        .rememberMeParameter("remember-me")   // html 체크박스의 name 속성
                        .tokenValiditySeconds(60 * 60 * 24 * 7)   //토큰 유지 시간(7일)
                )
                .logout(logout -> logout
                        .logoutUrl("/auth/logout")          // POST 요청을 받을 URL
                        .logoutSuccessUrl("/")              // 로그아웃 성공 후 이동
                        .invalidateHttpSession(true)        // 세션 무효화
                        .deleteCookies("JSESSIONID", "remember-me")  // 쿠키 삭제
                        .permitAll()
                );

        return http.build();

    }
}
