package com.knoc.auth.repository;

import com.knoc.auth.verification.EmailVerification;
import com.knoc.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Integer> {

    Optional<EmailVerification> findByMember(Member member);

    void deleteByExpiredAtBeforeAndIsVerifiedFalse(LocalDateTime now);
}
