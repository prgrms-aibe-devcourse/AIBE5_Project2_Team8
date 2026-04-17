package com.knoc.auth.service;

import com.knoc.auth.repository.EmailVerificationRepository;
import com.knoc.auth.verification.EmailVerification;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import com.knoc.member.MemberRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class EmailVerificationServiceTest {
    @Autowired
    private EmailVerificationService emailVerificationService;
    @Autowired
    private EmailVerificationRepository emailVerificationRepository;
    @Autowired
    private MemberRepository memberRepository;

    @MockitoBean
    EmailService emailService;

    private Member member;

    @BeforeEach
    void setUp() {
        member = Member.builder()
                .email("test@knoc.com")
                .password("password123!")
                .nickname("testuser")
                .build();
        memberRepository.save(member);
    }

    @Test
    @DisplayName("이미 인증 완료한 사용자가 재요청 시 IllegalStateException 발생")
    void sendCode_isVerified() {
        // given
        EmailVerification existing = EmailVerification.builder()
                .member(member)
                .companyEmail("test@company.com")
                .verificationCode("123456")
                .expiredAt(LocalDateTime.now().plusMinutes(5))
                .build();
        existing.verify();  // isVerified = true
        emailVerificationRepository.save(existing);

        // when & then
        assertThatThrownBy(() -> emailVerificationService.sendCode(member, "test@company.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 인증이 완료된 이메일입니다.");
    }

    @Test
    @DisplayName("일반 이메일 도메인 입력 시 IllegalArgumentException 발생")
    void sendCode_exclude() {
        assertThatThrownBy(() -> emailVerificationService.sendCode(member, "test@naver.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("기업 이메일만");
    }

    @Test
    @DisplayName("올바른 인증번호 입력 시 SENIOR 권한으로 변경")
    void verifyCode_promote_Senior() {
        // given
        EmailVerification verification = EmailVerification.builder()
                .member(member)
                .companyEmail("test@company.com")
                .verificationCode("123456")
                .expiredAt(LocalDateTime.now().plusMinutes(5))
                .build();
        emailVerificationRepository.save(verification);

        // when
        emailVerificationService.verifyCode(member, "123456");

        // then
        Member updated = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(updated.getRole()).isEqualTo(MemberRole.SENIOR);
    }
}

