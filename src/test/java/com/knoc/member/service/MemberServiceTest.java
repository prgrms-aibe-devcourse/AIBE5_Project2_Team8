package com.knoc.member.service;

import com.knoc.auth.dto.SignUpDto;
import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import com.knoc.global.service.FileStorageService;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import com.knoc.member.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock MemberRepository memberRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock FileStorageService fileStorageService;
    @InjectMocks MemberService memberService;

    // ===== registerMember =====

    @Test
    @DisplayName("회원가입 성공")
    void registerMember_success() {
        SignUpDto dto = signUpDto("user@test.com", "홍길동", "Pass123!");

        given(memberRepository.existsByEmail(dto.getEmail())).willReturn(false);
        given(memberRepository.existsByNickname(dto.getNickname())).willReturn(false);
        given(passwordEncoder.encode(dto.getPassword())).willReturn("encoded");

        memberService.registerMember(dto);

        verify(memberRepository).save(any(Member.class));
    }

    @Test
    @DisplayName("회원가입 실패: 이메일 중복 → EMAIL_ALREADY_EXISTS")
    void registerMember_duplicateEmail() {
        SignUpDto dto = signUpDto("dup@test.com", "홍길동", "Pass123!");

        given(memberRepository.existsByEmail(dto.getEmail())).willReturn(true);

        assertThatThrownBy(() -> memberService.registerMember(dto))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));

        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("회원가입 실패: 닉네임 중복 → NICKNAME_ALREADY_EXISTS")
    void registerMember_duplicateNickname() {
        SignUpDto dto = signUpDto("new@test.com", "중복닉네임", "Pass123!");

        given(memberRepository.existsByEmail(dto.getEmail())).willReturn(false);
        given(memberRepository.existsByNickname(dto.getNickname())).willReturn(true);

        assertThatThrownBy(() -> memberService.registerMember(dto))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NICKNAME_ALREADY_EXISTS));

        verify(memberRepository, never()).save(any());
    }

    // ===== updateProfile =====

    @Test
    @DisplayName("프로필 수정 성공: 파일 없이 닉네임만 변경")
    void updateProfile_nicknameOnly() {
        Member member = buildMember("user@test.com", "기존닉네임");
        given(memberRepository.findByEmail("user@test.com")).willReturn(Optional.of(member));
        given(memberRepository.existsByNickname("새닉네임")).willReturn(false);

        memberService.updateProfile("user@test.com", "새닉네임", null);

        assertThat(member.getNickname()).isEqualTo("새닉네임");
        verify(fileStorageService, never()).store(any());
    }

    @Test
    @DisplayName("프로필 수정 실패: 닉네임 중복 → NICKNAME_ALREADY_EXISTS")
    void updateProfile_duplicateNickname() {
        Member member = buildMember("user@test.com", "기존닉네임");
        given(memberRepository.findByEmail("user@test.com")).willReturn(Optional.of(member));
        given(memberRepository.existsByNickname("중복닉네임")).willReturn(true);

        assertThatThrownBy(() -> memberService.updateProfile("user@test.com", "중복닉네임", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NICKNAME_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("프로필 수정 실패: 존재하지 않는 회원 → MEMBER_NOT_FOUND")
    void updateProfile_memberNotFound() {
        given(memberRepository.findByEmail("ghost@test.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.updateProfile("ghost@test.com", "닉네임", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Test
    @DisplayName("프로필 수정 성공: 본인 닉네임 그대로 저장해도 중복 검사 통과")
    void updateProfile_sameNickname() {
        Member member = buildMember("user@test.com", "기존닉네임");
        given(memberRepository.findByEmail("user@test.com")).willReturn(Optional.of(member));

        memberService.updateProfile("user@test.com", "기존닉네임", null);

        verify(memberRepository, never()).existsByNickname(any());
    }

    // ===== helpers =====

    private SignUpDto signUpDto(String email, String nickname, String password) {
        SignUpDto dto = new SignUpDto();
        dto.setEmail(email);
        dto.setNickname(nickname);
        dto.setPassword(password);
        return dto;
    }

    private Member buildMember(String email, String nickname) {
        return Member.builder()
                .email(email)
                .password("encoded")
                .nickname(nickname)
                .role(MemberRole.USER)
                .build();
    }
}