package com.knoc.auth.service;

import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;
    @Override
    public UserDetails loadUserByUsername(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("해당 이메일로 가입된 유저가 없습니다: " + email));

        return User.builder()
                .username(member.getEmail())
                .password(member.getPassword())
                .roles(member.getRole().name()) // authorities -> roles로 변경(Spring Security 표준에 맞추기 위함)
                .build();

    }

}
