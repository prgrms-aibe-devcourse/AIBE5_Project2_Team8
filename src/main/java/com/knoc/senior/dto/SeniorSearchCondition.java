package com.knoc.senior.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SeniorSearchCondition {
    private String keyword;      // 닉네임, 회사명, 직군 검색
    private Integer careerYears; // 연차 필터 (0, 1, 3, 5, 10)
    private Integer minPrice;    // 최소 가격
    private Integer maxPrice;    // 최대 가격
    private String skill;        // 기술 스택 필터
}