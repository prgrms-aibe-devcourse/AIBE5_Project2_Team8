package com.knoc.settlement.entity;

import com.knoc.member.Member;
import com.knoc.order.entity.Order;
import com.knoc.senior.entity.SeniorProfile;
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
@Table(name = "review_feedback")
public class ReviewFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "junior_id", nullable = false)
    private Member junior;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "senior_profile_id", nullable = false)
    private SeniorProfile seniorProfile;

    @Column(nullable = false)
    private byte rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public ReviewFeedback(Order order, Member junior, SeniorProfile seniorProfile,
                          byte rating, String comment) {
        this.order = order;
        this.junior = junior;
        this.seniorProfile = seniorProfile;
        this.rating = rating;
        this.comment = comment;
    }
}