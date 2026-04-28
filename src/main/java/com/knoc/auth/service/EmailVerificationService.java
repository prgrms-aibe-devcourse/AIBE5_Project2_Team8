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
    private final MemberRepository memberRepository;
    private final EmailService emailService;


    /* 차단할 도메인 리스트
    application.properties 에서 차단할 도메인을 추가/수정/삭제할 수 있습니다.
    추가된 도메인으로 인증코드를 보내려는 시도를 차단합니다.
     */
    @Value("${email.blocked-domains}")
    private List<String> blockedDomains;

    /* 도메인 검증
    blockedDomains가 이메일 양식에 포함되어 있으면, BusinessException을 일으킵니다. (INVALID_EMAIL_DOMAIN)
     */
    public void validateEmail(String email) {
        String domain = email.substring(email.indexOf("@") + 1).toLowerCase();
        if (blockedDomains.contains(domain)) {
            throw new BusinessException(ErrorCode.INVALID_EMAIL_DOMAIN);
        }
    }

    /* 6자리 난수 생성기
    인증메일을 보낼 때와 검증할 때 쓰이는 인증코드를 생성해줄 메소드입니다.
    6자리 난수를 생성하고, 빈 곳은 0으로 채워줍니다.
     */
    public String generateCode(){
        Random random = new Random();
        return String.format("%06d", random.nextInt(1000000));
    }

    /* 검증된 이메일을 가지고 있는가?
    주니어 사용자가 이미 검증된 이메일을 가짐 -> 시니어라는 뜻으로 인증할 필요 없음을 알려주기 위함
     */
    public boolean isVerified(Member member){
        return emailVerificationRepository.findByMember(member)
                .map(EmailVerification::isVerified)
                .orElse(false);
    }


    /* sendCode - 검증(도메인, 시니어체크), 인증번호 생성, 메일 보내기 */
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

    /* verifyCode - 사용자로부터 받은 인증번호를 검증(시간만료여부, 정확하게 입력했는지) */
    @Transactional
    public void verifyCode(Member member, String inputCode){
        // 인증 기록이 없다면 sendCode에서 받은 기록이 없다는 얘기이므로, 에러를 던진다. (비정상적인접근)
        EmailVerification verification = emailVerificationRepository.findByMember(member)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_VERIFICATION_NOT_FOUND));

        if (LocalDateTime.now().isAfter(verification.getExpiredAt())) {
            throw new BusinessException(ErrorCode.EXPIRED_VERIFICATION_CODE);
        }

        if (!verification.getVerificationCode().equals(inputCode)) {
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE);
        }

        // DirtyCheck 으로 변경감지 (자동저장)
        verification.verify();
        member.promoteToSenior();
        memberRepository.save(member);
    }

}
