package com.knoc.member.repository;

import com.knoc.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    // 이메일로 member 조회(Spring Security 및 로그인에 사용)
    Optional<Member> findByEmail(String email);

    // 회원가입 시 이메일 중복 검증
    boolean existsByEmail(String email);

    // 회원가입 및 정보 수정 시 닉네임 중복 검증
    boolean existsByNickname(String nickname);
}
