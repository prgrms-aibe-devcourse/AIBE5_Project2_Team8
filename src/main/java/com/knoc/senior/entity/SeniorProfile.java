package com.knoc.senior.entity;

import com.knoc.global.entity.BaseEntity;
import com.knoc.member.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "senior_profile")
public class SeniorProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private Member member;

    @Column(nullable = false, length = 100)
    private String company;

    @Column(nullable = false, length = 100)
    private String position;

    @Column(name = "career_years", nullable = false)
    private int careerYears;

    @Column(columnDefinition = "TEXT")
    private String introduction;

    @Column(name = "linkedin_url", length = 512)
    private String linkedinUrl;

    @Column(name = "price_per_review", nullable = false)
    private int pricePerReview;

    @Column(name = "avg_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal avgRating;

    @Column(name = "total_review_count", nullable = false)
    private int totalReviewCount;

    @OneToMany(mappedBy = "seniorProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SeniorSkill> skills = new ArrayList<>();

    @OneToMany(mappedBy = "seniorProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SeniorCareer> careers = new ArrayList<>();

    @Builder
    public SeniorProfile(Member member, String company, String position, int careerYears,
                         String introduction, String linkedinUrl, int pricePerReview) {
        this.member = member;
        this.company = company;
        this.position = position;
        this.careerYears = careerYears;
        this.introduction = introduction;
        this.linkedinUrl = linkedinUrl;
        this.pricePerReview = pricePerReview;
        this.avgRating = BigDecimal.ZERO;
        this.totalReviewCount = 0;
    }

    //==연관관계 편의 매서드==
    public void addSkill(SeniorSkill skill) {
        this.skills.add(skill);
        //무한 루프 방지
        if (skill.getSeniorProfile() != this) {
            skill.assignSeniorProfile(this);
        }
    }

    public void addCareer(SeniorCareer career) {
        this.careers.add(career);
        if (career.getSeniorProfile() != this) {
            career.assignSeniorProfile(this);
        }
    }

    //==프로필 수정==
    public void update(String company, String position, int careerYears,
                       String introduction, String linkedinUrl, int pricePerReview) {
        this.company = company;
        this.position = position;
        this.careerYears = careerYears;
        this.introduction = introduction;
        this.linkedinUrl = linkedinUrl;
        this.pricePerReview = pricePerReview;
    }

    public void clearSkills() {
        this.skills.clear();
    }

    public void clearCareers() {
        this.careers.clear();
    }
}