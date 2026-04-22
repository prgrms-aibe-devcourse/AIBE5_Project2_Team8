package com.knoc.auth.verification;

import com.knoc.auth.repository.EmailVerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailVerificationScheduler {

    private final EmailVerificationRepository emailVerificationRepository;

    @Transactional
    // 테스트용으로 fixedDelay = 10000 으로 10초마다 설정
    // 통상시에는 @Scheduled(cron = "0 0 * * * *") 으로 변경 바람
    @Scheduled(cron = "0 0 * * * *")
    public void deleteExpiredVerification() {

        long before = emailVerificationRepository.count();

        emailVerificationRepository.deleteByExpiredAtBeforeAndIsVerifiedFalse(LocalDateTime.now());

        long after = emailVerificationRepository.count();

        log.info("[스케줄러 실행] 만료 인증 데이터 정리 - 삭제 전 : {} 건, 삭제 후 : {} 건", before, after);
    }
}
