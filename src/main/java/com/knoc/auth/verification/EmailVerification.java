package com.knoc.auth.verification;

import com.knoc.member.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "email_verification")
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "company_email", nullable = false)
    private String companyEmail;

    @Column(name = "verification_code", nullable = false, length = 6)
    private String verificationCode;

    @Column(name = "is_verified", nullable = false)
    private boolean isVerified;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public EmailVerification(Member member, String companyEmail, String verificationCode, LocalDateTime expiredAt) {
        this.member = member;
        this.companyEmail = companyEmail;
        this.verificationCode = verificationCode;
        this.isVerified = false;
        this.expiredAt = expiredAt;
    }

    public void verify() {
        this.isVerified = true;
    }
}