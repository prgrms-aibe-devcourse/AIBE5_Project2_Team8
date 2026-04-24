package com.knoc.senior;

import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import com.knoc.senior.dto.SeniorDetailResponseDto;
import com.knoc.senior.dto.SeniorProfileRequestDto;
import com.knoc.senior.dto.SeniorProfileResponseDto;
import com.knoc.senior.dto.SeniorSearchCondition;
import com.knoc.senior.entity.SeniorCareer;
import com.knoc.senior.entity.SeniorProfile;
import com.knoc.senior.entity.SeniorSkill;
import com.knoc.senior.repository.SeniorProfileQueryRepository;
import com.knoc.senior.repository.SeniorProfileRepository;
import com.knoc.settlement.repository.ReviewFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeniorProfileService {
    private final SeniorProfileRepository seniorProfileRepository;
    private final SeniorProfileQueryRepository seniorProfileQueryRepository;
    private final MemberRepository memberRepository;
    private final ReviewFeedbackRepository reviewFeedbackRepository;

    // 시니어 목록 검색 (QueryDSL)
    public List<SeniorProfileResponseDto> searchProfiles(SeniorSearchCondition condition) {
        return seniorProfileQueryRepository.search(condition).stream()
                .map(SeniorProfileResponseDto::from)
                .collect(Collectors.toList());
    }

    // 시니어 상세 프로필 조회 (ID 기반)
    public SeniorDetailResponseDto getDetailById(Long id) {
        SeniorProfile profile = seniorProfileRepository.findById(id)
                .orElseThrow(() ->  new BusinessException(ErrorCode.SENIOR_PROFILE_NOT_FOUND));

        // 최신 리뷰
        List<SeniorDetailResponseDto.ReviewDto> latestReviews =
                reviewFeedbackRepository.findTop3BySeniorProfile_IdOrderByCreatedAtDesc(id)
                        .stream()
                        .map(r -> SeniorDetailResponseDto.ReviewDto.builder()
                                .juniorNickname(r.getJunior().getNickname())
                                .rating(r.getRating())
                                .comment(r.getComment())
                                .timeAgo(timeAgo(r.getCreatedAt()))
                                .build())
                        .collect(Collectors.toList());

        return SeniorDetailResponseDto.from(profile, latestReviews);
    }

    public String timeAgo(LocalDateTime createdAt) {
        if (createdAt == null) return "";
        // 리뷰 생성일과 현재 날짜를 비교
        long days = ChronoUnit.DAYS.between(createdAt, LocalDateTime.now());
        if (days == 0) return "오늘";
        else if (days < 7) return days + "일 전";
        else if (days < 30) return (days / 7) + "주 전";
        return (days / 30) + "개월 전";
    }

    // 시니어 프로필 조회
    public SeniorProfileResponseDto getProfile(Long memberId) {
        SeniorProfile profile = seniorProfileRepository.findByMemberId(memberId)
                .orElseThrow(() ->  new BusinessException(ErrorCode.SENIOR_PROFILE_NOT_FOUND));
        return SeniorProfileResponseDto.from(profile);
    }

    // 시니어 프로필 수정
    @Transactional
    public void updateProfile(Long memberId, SeniorProfileRequestDto dto) {
        SeniorProfile profile = seniorProfileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SENIOR_PROFILE_NOT_FOUND));

        // 기본 필드 수정
        profile.update(
                dto.getCompany(),
                dto.getPosition(),
                dto.getCareerYears(),
                dto.getIntroduction(),
                dto.getLinkedinUrl(),
                dto.getPricePerReview()
        );

        // 스킬 전체 교체 (orphanRemoval로 기존 스킬 자동 삭제)
        profile.clearSkills();
        if (dto.getSkills() != null) {
            dto.getSkills().forEach(skillName -> {
                SeniorSkill skill = SeniorSkill.builder().skillName(skillName).build();
                profile.addSkill(skill);
            });
        }

        // 경력 전체 교체 (orphanRemoval로 기존 경력 자동 삭제)
        profile.clearCareers();
        if (dto.getCareers() != null) {
            dto.getCareers().forEach(careerDto -> {
                SeniorCareer career = SeniorCareer.builder()
                        .companyName(careerDto.getCompanyName())
                        .position(careerDto.getPosition())
                        .startDate(careerDto.getStartDate())
                        .endDate(careerDto.getEndDate())
                        .build();
                profile.addCareer(career);
            });
        }
    }

    //시니어 프로필 생성
    @Transactional
    public Long createProfile(Long memberId, SeniorProfileRequestDto dto) {
        //프로필 작성하는 member 조회
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

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
