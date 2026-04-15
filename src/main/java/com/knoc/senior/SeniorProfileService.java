package com.knoc.senior;

import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import com.knoc.senior.dto.SeniorProfileRequestDto;
import com.knoc.senior.entity.SeniorCareer;
import com.knoc.senior.entity.SeniorProfile;
import com.knoc.senior.entity.SeniorSkill;
import com.knoc.senior.repository.SeniorProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeniorProfileService {
    private final SeniorProfileRepository seniorProfileRepository;
    private final MemberRepository memberRepository;

    //시니어 프로필 생성
    @Transactional
    public Long createProfile(Long memberId, SeniorProfileRequestDto dto) {
        //프로필 작성하는 member 조회
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원 입니다."));

        //부모 엔티티(SeniorProfile) 생성
        SeniorProfile profile = SeniorProfile.builder()
                .member(member)
                .company(dto.getCompany())
                .position(dto.getPosition())
                .careerYears(dto.getCareerYears())
                .introduction(dto.getIntroduction())
                .linkedinUrl(dto.getLinkedinUrl())
                .pricePerReview(dto.getPricePerReview())
                .build();

        //스킬(자식) 생성 및 부모에 묶기
        if (dto.getSkills() != null) {
            dto.getSkills().forEach(skillName->{
                SeniorSkill skill = SeniorSkill.builder().skillName(skillName).build();
                profile.addSkill(skill);
            });
        }
        // 경력(자식) 생성 및 부모에 묶기
        if (dto.getCareers() != null) {
            dto.getCareers().forEach(careerDto -> {
                SeniorCareer career = SeniorCareer.builder()
                        .companyName(careerDto.getCompanyName())
                        .position(careerDto.getPosition())
                        .startDate(careerDto.getStartDate())
                        .endDate(careerDto.getEndDate())
                        .build();
                profile.addCareer(career); // 알아서 양방향 세팅 완료!
            });
        }
        return seniorProfileRepository.save(profile).getId();

    }
}
