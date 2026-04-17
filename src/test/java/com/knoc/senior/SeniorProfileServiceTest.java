package com.knoc.senior;

import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import com.knoc.senior.dto.SeniorProfileRequestDto;
import com.knoc.senior.dto.SeniorProfileResponseDto;
import com.knoc.senior.dto.SeniorSearchCondition;
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
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SENIOR_PROFILE_NOT_FOUND);;
    }

    // ═══════════════════════════════════════════════════════
    //  searchProfiles 테스트
    // ═══════════════════════════════════════════════════════

    /** setUp()의 프로필 외에 추가 프로필을 하나 더 생성하는 헬퍼 */
    private void createExtraProfile(String email, String nickname, String company,
                                    String position, int careerYears, int price,
                                    List<String> skills) {
        Member member = Member.builder()
                .email(email)
                .password("password!")
                .nickname(nickname)
                .build();
        memberRepository.save(member);

        SeniorProfile profile = SeniorProfile.builder()
                .member(member)
                .company(company)
                .position(position)
                .careerYears(careerYears)
                .introduction("소개글")
                .linkedinUrl(null)
                .pricePerReview(price)
                .build();
        skills.forEach(s -> profile.addSkill(SeniorSkill.builder().skillName(s).build()));
        seniorProfileRepository.save(profile);
    }

    @Test
    @DisplayName("조건 없으면 전체 프로필을 반환한다")
    void searchProfiles_조건없음_전체반환() {
        // given
        createExtraProfile("extra@knoc.com", "추가유저", "네이버", "프론트엔드 개발자",
                5, 70000, List.of("React", "TypeScript"));
        em.flush();
        em.clear();

        SeniorSearchCondition cond = new SeniorSearchCondition();

        // when
        List<SeniorProfileResponseDto> result = seniorProfileService.searchProfiles(cond);

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("닉네임 키워드로 검색하면 일치하는 프로필만 반환한다")
    void searchProfiles_키워드_닉네임검색() {
        // given
        createExtraProfile("extra@knoc.com", "추가유저", "네이버", "프론트엔드 개발자",
                5, 70000, List.of("React"));
        em.flush();
        em.clear();

        SeniorSearchCondition cond = new SeniorSearchCondition();
        cond.setKeyword("시니어");    // setUp() 닉네임: "시니어유저"

        // when
        List<SeniorProfileResponseDto> result = seniorProfileService.searchProfiles(cond);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNickname()).isEqualTo("시니어유저");
    }

    @Test
    @DisplayName("회사명 키워드로 검색하면 일치하는 프로필만 반환한다")
    void searchProfiles_키워드_회사명검색() {
        // given
        createExtraProfile("extra@knoc.com", "추가유저", "네이버", "프론트엔드 개발자",
                5, 70000, List.of("React"));
        em.flush();
        em.clear();

        SeniorSearchCondition cond = new SeniorSearchCondition();
        cond.setKeyword("네이버");

        // when
        List<SeniorProfileResponseDto> result = seniorProfileService.searchProfiles(cond);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCompany()).isEqualTo("네이버");
    }

    @Test
    @DisplayName("직군 키워드로 검색하면 일치하는 프로필만 반환한다")
    void searchProfiles_키워드_직군검색() {
        // given
        createExtraProfile("extra@knoc.com", "추가유저", "네이버", "프론트엔드 개발자",
                5, 70000, List.of("React"));
        em.flush();
        em.clear();

        SeniorSearchCondition cond = new SeniorSearchCondition();
        cond.setKeyword("프론트엔드");

        // when
        List<SeniorProfileResponseDto> result = seniorProfileService.searchProfiles(cond);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPosition()).isEqualTo("프론트엔드 개발자");
    }

    @Test
    @DisplayName("연차 필터를 설정하면 해당 연차 이상인 프로필만 반환한다")
    void searchProfiles_연차필터() {
        // given: setUp() careerYears=3, 추가 careerYears=8
        createExtraProfile("extra@knoc.com", "추가유저", "네이버", "프론트엔드 개발자",
                8, 70000, List.of("React"));
        em.flush();
        em.clear();

        SeniorSearchCondition cond = new SeniorSearchCondition();
        cond.setCareerYears(5);    // 5년 이상 → careerYears=8만 해당

        // when
        List<SeniorProfileResponseDto> result = seniorProfileService.searchProfiles(cond);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCareerYears()).isGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("최대 가격 필터를 설정하면 해당 가격 이하인 프로필만 반환한다")
    void searchProfiles_최대가격필터() {
        // given: setUp() price=50000, 추가 price=100000
        createExtraProfile("extra@knoc.com", "추가유저", "네이버", "프론트엔드 개발자",
                5, 100000, List.of("React"));
        em.flush();
        em.clear();

        SeniorSearchCondition cond = new SeniorSearchCondition();
        cond.setMaxPrice(60000);    // 60,000 이하 → price=50000만 해당

        // when
        List<SeniorProfileResponseDto> result = seniorProfileService.searchProfiles(cond);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPricePerReview()).isLessThanOrEqualTo(60000);
    }

    @Test
    @DisplayName("스킬 필터를 설정하면 해당 스킬을 보유한 프로필만 반환한다")
    void searchProfiles_스킬필터() {
        // given: setUp() skills=[Java, Spring], 추가 skills=[React]
        createExtraProfile("extra@knoc.com", "추가유저", "네이버", "프론트엔드 개발자",
                5, 70000, List.of("React", "TypeScript"));
        em.flush();
        em.clear();

        SeniorSearchCondition cond = new SeniorSearchCondition();
        cond.setSkill("React");

        // when
        List<SeniorProfileResponseDto> result = seniorProfileService.searchProfiles(cond);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSkills()).contains("React");
    }

    @Test
    @DisplayName("여러 조건을 동시에 적용하면 모든 조건을 만족하는 프로필만 반환한다")
    void searchProfiles_복합조건() {
        // given: setUp() 카카오/Java/price=50000/career=3
        //        추가 네이버/React/price=80000/career=7
        createExtraProfile("extra@knoc.com", "추가유저", "네이버", "프론트엔드 개발자",
                7, 80000, List.of("React"));
        em.flush();
        em.clear();

        SeniorSearchCondition cond = new SeniorSearchCondition();
        cond.setKeyword("카카오");
        cond.setMaxPrice(60000);
        cond.setSkill("Java");

        // when
        List<SeniorProfileResponseDto> result = seniorProfileService.searchProfiles(cond);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCompany()).isEqualTo("카카오");
        assertThat(result.get(0).getSkills()).contains("Java");
    }

    @Test
    @DisplayName("조건에 맞는 프로필이 없으면 빈 리스트를 반환한다")
    void searchProfiles_결과없음() {
        // given
        em.flush();
        em.clear();

        SeniorSearchCondition cond = new SeniorSearchCondition();
        cond.setKeyword("존재하지않는닉네임XYZ");

        // when
        List<SeniorProfileResponseDto> result = seniorProfileService.searchProfiles(cond);

        // then
        assertThat(result).isEmpty();
    }
}