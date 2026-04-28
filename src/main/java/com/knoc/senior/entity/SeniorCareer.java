package com.knoc.senior.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "senior_career")
public class SeniorCareer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "senior_profile_id", nullable = false)
    private SeniorProfile seniorProfile;

    @Column(name = "company_name", nullable = false, length = 100)
    private String companyName;

    @Column(nullable = false, length = 100)
    private String position;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Builder
    public SeniorCareer(SeniorProfile seniorProfile, String companyName, String position,
                        LocalDate startDate, LocalDate endDate) {
        this.seniorProfile = seniorProfile;
        this.companyName = companyName;
        this.position = position;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    //==연관관계 편의 매서드
    protected void assignSeniorProfile(SeniorProfile seniorProfile) {
        this.seniorProfile = seniorProfile;
    }
}