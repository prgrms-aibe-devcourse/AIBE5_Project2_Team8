package com.knoc.senior;

import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import com.knoc.senior.dto.SeniorProfileRequestDto;
import com.knoc.senior.dto.SeniorProfileResponseDto;
import com.knoc.senior.entity.SeniorCareer;
import com.knoc.senior.entity.SeniorProfile;
import com.knoc.senior.entity.SeniorSkill;
import com.knoc.senior.repository.SeniorProfileRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SeniorProfileServiceTest {

    @Autowired
    private SeniorProfileService seniorProfileService;

    @Autowired
    private SeniorProfileRepository seniorProfileRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EntityManager em;

    private Long memberId;

    @BeforeEach
    void setUp() {
        // 테스트용 회원 생성
        Member member = Member.builder()
                .email("senior@knoc.com")
                .password("password123!")
                .nickname("시니어유저")
                .build();
        memberRepository.save(member);
        memberId = member.getId();

        // 수정 대상 프로필 생성
        SeniorProfile profile = SeniorProfile.builder()
                .member(member)
                .company("카카오")
                .position("백엔드 개발자")
                .careerYears(3)
                .introduction("안녕하세요.")
                .linkedinUrl("https://linkedin.com/in/before")
                .pricePerReview(50000)
                .build();

        profile.addSkill(SeniorSkill.builder().skillName("Java").build());
        profile.addSkill(SeniorSkill.builder().skillName("Spring").build());

        profile.addCareer(SeniorCareer.builder()
                .companyName("우아한형제들")
                .position("백엔드")
                .startDate(LocalDate.of(2020, 1, 1))
                .endDate(LocalDate.of(2022, 12, 31))
                .build());

        seniorProfileRepository.save(profile);
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("기본 프로필 필드(회사, 직무, 연차, 소개, 링크드인, 단가)가 수정된다")
    void updateProfile_기본필드_수정() {
        // given
        SeniorProfileRequestDto dto = new SeniorProfileRequestDto();
        dto.setCompany("토스");
        dto.setPosition("서버 개발자");
        dto.setCareerYears(7);
        dto.setIntroduction("반갑습니다.");
        dto.setLinkedinUrl("https://linkedin.com/in/after");
        dto.setPricePerReview(80000);

        // when
        seniorProfileService.updateProfile(memberId, dto);
        em.flush();
        em.clear();

        // then
        SeniorProfileResponseDto result = seniorProfileService.getProfile(memberId);
        assertThat(result.getCompany()).isEqualTo("토스");
        assertThat(result.getPosition()).isEqualTo("서버 개발자");
        assertThat(result.getCareerYears()).isEqualTo(7);
        assertThat(result.getIntroduction()).isEqualTo("반갑습니다.");
        assertThat(result.getLinkedinUrl()).isEqualTo("https://linkedin.com/in/after");
        assertThat(result.getPricePerReview()).isEqualTo(80000);
    }

    @Test
    @DisplayName("스킬 목록이 새 목록으로 전체 교체된다")
    void updateProfile_스킬_전체_교체() {
        // given
        SeniorProfileRequestDto dto = new SeniorProfileRequestDto();
        dto.setCompany("토스");
        dto.setPosition("서버 개발자");
        dto.setCareerYears(7);
        dto.setPricePerReview(80000);
        dto.setSkills(List.of("Kotlin", "Redis", "Kafka")); // 기존 Java, Spring → 교체

        // when
        seniorProfileService.updateProfile(memberId, dto);
        em.flush();
        em.clear();

        // then
        SeniorProfileResponseDto result = seniorProfileService.getProfile(memberId);
        assertThat(result.getSkills()).hasSize(3);
        assertThat(result.getSkills()).containsExactlyInAnyOrder("Kotlin", "Redis", "Kafka");
        assertThat(result.getSkills()).doesNotContain("Java", "Spring");
    }

    @Test
    @DisplayName("경력 목록이 새 목록으로 전체 교체된다")
    void updateProfile_경력_전체_교체() {
        // given
        SeniorProfileRequestDto dto = new SeniorProfileRequestDto();
        dto.setCompany("토스");
        dto.setPosition("서버 개발자");
        dto.setCareerYears(7);
        dto.setPricePerReview(80000);

        SeniorProfileRequestDto.CareerDto newCareer = new SeniorProfileRequestDto.CareerDto();
        newCareer.setCompanyName("네이버");
        newCareer.setPosition("풀스택");
        newCareer.setStartDate(LocalDate.of(2021, 3, 1));
        newCareer.setEndDate(LocalDate.of(2024, 2, 28));
        dto.setCareers(List.of(newCareer));

        // when
        seniorProfileService.updateProfile(memberId, dto);
        em.flush();
        em.clear();

        // then
        SeniorProfileResponseDto result = seniorProfileService.getProfile(memberId);
        assertThat(result.getCareers()).hasSize(1);
        assertThat(result.getCareers().get(0).getCompanyName()).isEqualTo("네이버");
        assertThat(result.getCareers().get(0).getPosition()).isEqualTo("풀스택");
        assertThat(result.getCareers().get(0).getStartDate()).isEqualTo(LocalDate.of(2021, 3, 1));
    }

    @Test
    @DisplayName("스킬을 null로 전달하면 기존 스킬이 모두 삭제된다")
    void updateProfile_스킬_null이면_전체삭제() {
        // given
        SeniorProfileRequestDto dto = new SeniorProfileRequestDto();
        dto.setCompany("토스");
        dto.setPosition("서버 개발자");
        dto.setCareerYears(7);
        dto.setPricePerReview(80000);
        dto.setSkills(null);

        // when
        seniorProfileService.updateProfile(memberId, dto);
        em.flush();
        em.clear();

        // then
        SeniorProfileResponseDto result = seniorProfileService.getProfile(memberId);
        assertThat(result.getSkills()).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 memberId로 수정 시 예외가 발생한다")
    void updateProfile_없는회원_예외() {
        // given
        Long nonExistentMemberId = 9999L;
        SeniorProfileRequestDto dto = new SeniorProfileRequestDto();
        dto.setCompany("토스");
        dto.setPosition("서버 개발자");
        dto.setCareerYears(5);
        dto.setPricePerReview(70000);

        // when & then
        assertThatThrownBy(() -> seniorProfileService.updateProfile(nonExistentMemberId, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("시니어 프로필이 존재하지 않습니다.");
    }
}