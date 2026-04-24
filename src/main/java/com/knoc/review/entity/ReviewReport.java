package com.knoc.review.entity;

import com.knoc.global.entity.BaseEntity;
import com.knoc.member.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "review_report")
public class ReviewReport extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_request_id", nullable = false, unique = true)
    private ReviewRequest reviewRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "senior_id", nullable = false)
    private Member senior;

    @Column(name = "industry_perspective", nullable = false, columnDefinition = "TEXT")
    private String industryPerspective;

    @Column(name = "edge_cases", nullable = false, columnDefinition = "TEXT")
    private String edgeCases;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String alternatives;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Builder
    public ReviewReport(ReviewRequest reviewRequest, Member senior, String industryPerspective,
                        String edgeCases, String alternatives, String aiSummary) {
        this.reviewRequest = reviewRequest;
        this.senior = senior;
        this.industryPerspective = industryPerspective;
        this.edgeCases = edgeCases;
        this.alternatives = alternatives;
        this.aiSummary = aiSummary;
    }

    public void update(String industryPerspective, String edgeCases, String alternatives) {
        this.industryPerspective = industryPerspective;
        this.edgeCases = edgeCases;
        this.alternatives = alternatives;
    }
}