package com.knoc.senior.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "senior_skill")
public class SeniorSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "senior_profile_id", nullable = false)
    private SeniorProfile seniorProfile;

    @Column(name = "skill_name", nullable = false, length = 50)
    private String skillName;

    @Builder
    public SeniorSkill(SeniorProfile seniorProfile, String skillName) {
        this.seniorProfile = seniorProfile;
        this.skillName = skillName;
    }

    //==연관관계 편의 매서드
    protected void assignSeniorProfile(SeniorProfile seniorProfile) {
        this.seniorProfile = seniorProfile;
    }
}