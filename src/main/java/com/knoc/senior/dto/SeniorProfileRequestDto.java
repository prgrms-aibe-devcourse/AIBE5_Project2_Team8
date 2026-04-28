package com.knoc.senior.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class SeniorProfileRequestDto {
    private String company;
    private String position;
    private int careerYears;
    private String introduction;
    private String linkedinUrl;
    private int pricePerReview;

    //스킬 목록
    private List<String> skills;
    //경력 목록
    private List<CareerDto> careers;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CareerDto{
        private String companyName;
        private String position;
        private LocalDate startDate;
        private LocalDate endDate;
    }


}
