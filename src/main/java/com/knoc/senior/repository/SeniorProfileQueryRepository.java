package com.knoc.senior.repository;

import com.knoc.member.QMember;
import com.knoc.senior.dto.SeniorSearchCondition;
import com.knoc.senior.entity.QSeniorProfile;
import com.knoc.senior.entity.QSeniorSkill;
import com.knoc.senior.entity.SeniorProfile;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class SeniorProfileQueryRepository {

    private final JPAQueryFactory queryFactory;

    private static final QSeniorProfile profile = QSeniorProfile.seniorProfile;
    private static final QMember member = QMember.member;
    private static final QSeniorSkill skill = QSeniorSkill.seniorSkill;

    public List<SeniorProfile> search(SeniorSearchCondition cond) {
        return queryFactory
                .selectFrom(profile)
                .join(profile.member, member).fetchJoin()
                .leftJoin(profile.skills, skill)
                .where(
                        keywordContains(cond.getKeyword()),
                        careerYearsGoe(cond.getCareerYears()),
                        minPriceGoe(cond.getMinPrice()),
                        maxPriceLoe(cond.getMaxPrice()),
                        skillNameEq(cond.getSkill())
                )
                .distinct()
                .fetch();
    }

    // 키워드: 닉네임 OR 회사명 OR 직군
    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) return null;
        String pattern = "%" + keyword + "%";
        return member.nickname.likeIgnoreCase(pattern)
                .or(profile.company.likeIgnoreCase(pattern))
                .or(profile.position.likeIgnoreCase(pattern));
    }

    // 최소 연차 이상 (슬라이더: N년 이상)
    private BooleanExpression careerYearsGoe(Integer careerYears) {
        return (careerYears != null && careerYears > 0) ? profile.careerYears.goe(careerYears) : null;
    }

    // 최소 가격 이상
    private BooleanExpression minPriceGoe(Integer minPrice) {
        return minPrice != null ? profile.pricePerReview.goe(minPrice) : null;
    }

    // 최대 가격 이하
    private BooleanExpression maxPriceLoe(Integer maxPrice) {
        return maxPrice != null ? profile.pricePerReview.loe(maxPrice) : null;
    }

    // 기술 스택 일치
    private BooleanExpression skillNameEq(String skillName) {
        if (!StringUtils.hasText(skillName)) return null;
        return skill.skillName.equalsIgnoreCase(skillName);
    }
}