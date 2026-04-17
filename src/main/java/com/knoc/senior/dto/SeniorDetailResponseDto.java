package com.knoc.senior.dto;

import com.knoc.senior.entity.SeniorProfile;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public class SeniorDetailResponseDto {
    private final Long id;
    private final String nickname;
    private final String profileImageUrl;
    private final String company;
    private final String position;
    private final int careerYears;
    private final String introduction;
    private final String linkedinUrl;
    private final int pricePerReview;
    private final java.math.BigDecimal avgRating;
    private final int totalReviewCount;
    private final List<String> skills;
    private final List<SeniorDetailResponseDto.CareerDto> careers;


    public SeniorDetailResponseDto(SeniorProfile profile) {
        this.id = profile.getId();
        this.nickname = profile.getMember().getNickname();
        this.profileImageUrl = profile.getMember().getProfileImageUrl();
        this.company = profile.getCompany();
        this.position = profile.getPosition();
        this.careerYears = profile.getCareerYears();
        this.introduction = profile.getIntroduction();
        this.linkedinUrl = profile.getLinkedinUrl();
        this.pricePerReview = profile.getPricePerReview();
        this.avgRating = profile.getAvgRating();
        this.totalReviewCount = profile.getTotalReviewCount();
        this.skills = profile.getSkills().stream()
                .map(s -> s.getSkillName())
                .collect(Collectors.toList());
        this.careers = profile.getCareers().stream()
                .map(CareerDto::from)
                .collect(Collectors.toList());
    }
    public static SeniorDetailResponseDto from(SeniorProfile profile) {
        return new SeniorDetailResponseDto(profile);
    }

    @Getter
    public static class CareerDto {
        private final String companyName;
        private final String position;
        private final LocalDate startDate;
        private final LocalDate endDate;

        private CareerDto(String companyName, String position, LocalDate startDate, LocalDate endDate) {
            this.companyName = companyName;
            this.position = position;
            this.startDate = startDate;
            this.endDate = endDate;
        }

        public static CareerDto from(com.knoc.senior.entity.SeniorCareer career) {
            return new CareerDto(
                    career.getCompanyName(),
                    career.getPosition(),
                    career.getStartDate(),
                    career.getEndDate()
            );
        }
    }

}
