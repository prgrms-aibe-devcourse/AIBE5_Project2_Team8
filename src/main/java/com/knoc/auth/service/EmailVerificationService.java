package com.knoc.auth.service;

import com.knoc.auth.repository.EmailVerificationRepository;
import com.knoc.auth.verification.EmailVerification;
import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

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
            throw new BusinessException(ErrorCode.INVALID_EMAIL_DOMAIN);
        }
    }


    @Transactional
    public void sendCode(Member member, String companyEmail){
        validateEmail(companyEmail);
        String code = generateCode();

        EmailVerification verification = emailVerificationRepository.findByMember(member)
                .map(existing -> {
                    if(existing.isVerified()){
                        throw new BusinessException(ErrorCode.ALREADY_VERIFIED);
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
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_VERIFICATION_NOT_FOUND));

        if (LocalDateTime.now().isAfter(verification.getExpiredAt())) {
            throw new BusinessException(ErrorCode.EXPIRED_VERIFICATION_CODE);
        }

        if (!verification.getVerificationCode().equals(inputCode)) {
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE);
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
