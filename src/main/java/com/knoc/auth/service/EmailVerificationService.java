package com.knoc.auth.service;

import com.knoc.auth.repository.EmailVerificationRepository;
import com.knoc.auth.verification.EmailVerification;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {
    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailService emailService;


    @Value("${email.blocked-domains}")
    private List<String> blockedDomains;

    private final MemberRepository memberRepository;

    // 도메인 검증 코드
    public void validateEmail(String email) {
        String domain = email.substring(email.indexOf("@") + 1).toLowerCase();
        if (blockedDomains.contains(domain)) {
            throw new IllegalArgumentException("기업 이메일만 인증 가능합니다");
        }
    }


    @Transactional
    public void sendCode(Member member, String companyEmail){
        validateEmail(companyEmail);
        String code = generateCode();

        EmailVerification verification = emailVerificationRepository.findByMember(member)
                .map(existing -> {
                    if(existing.isVerified()){
                        throw new IllegalStateException("이미 인증이 완료된 이메일입니다.");
                    }
                    existing.updateVerificationCode(code, LocalDateTime.now().plusMinutes(5));
                    return existing;
                })
                .orElseGet(() -> EmailVerification.builder()
                        .member(member)
                        .companyEmail(companyEmail)
                        .verificationCode(code)
                        .expiredAt(LocalDateTime.now().plusMinutes(5))
                        .build());

        emailVerificationRepository.save(verification);
        emailService.send("Knoc 시니어 이메일 인증번호", "인증번호: " + code + "\n\n5분 후에 만료됩니다", companyEmail);
    }

    public void verifyCode(Member member, String inputCode){
        // 인증 번호 6자리 제한은 html form에서 처리하고
        EmailVerification verification = emailVerificationRepository.findByMember(member)
                .orElseThrow(() -> new IllegalArgumentException("인증 요청 내역이 없습니다"));

        if (LocalDateTime.now().isAfter(verification.getExpiredAt())) {
            throw new IllegalArgumentException("인증번호가 만료되었습니다");
        }

        if (!verification.getVerificationCode().equals(inputCode)) {
            throw new IllegalArgumentException("인증번호가 일치하지 않습니다");
        }

        verification.verify();
        emailVerificationRepository.save(verification);

        member.promoteToSenior();
        memberRepository.save(member);
    }

    public String generateCode(){
        Random random = new Random();
        return String.format("%06d", random.nextInt(1000000));
    }

    public boolean isVerified(Member member){
        return emailVerificationRepository.findByMember(member)
                .map(EmailVerification::isVerified)
                .orElse(false);
    }

}
