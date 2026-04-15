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
                        .requestMatchers("/auth/**").permitAll()
                        // 그외 나머지 경로는 인증을 해야만 접근 허용
                        .anyRequest().authenticated()
                )

                // 커스텀 폼 로그인 설정
                .formLogin(form -> form
                        .loginPage("/auth/login")
                        .permitAll()
                );

        // 나머지는 추후 추가할 예정
        return http.build();

    }
}
