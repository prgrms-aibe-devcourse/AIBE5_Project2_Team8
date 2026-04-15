package com.knoc.senior.repository;

import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import com.knoc.senior.entity.SeniorCareer;
import com.knoc.senior.entity.SeniorProfile;
import com.knoc.senior.entity.SeniorSkill;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SeniorProfileRepositoryTest {
    @Autowired
    private SeniorProfileRepository seniorProfileRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private EntityManager em;

    @Test
    public void 프로필_설정시_skill_career_DB에_저장() throws Exception
    {
        //given
        //회원생성
        Member member = Member.builder()
                .email("test@knoc.com")
                .password("password123!")
                .nickname("테스트유저")
                .build();
        memberRepository.save(member);
        //프로필을 생성
        SeniorProfile profile = SeniorProfile.builder()
                .member(member)
                .company("토스")
                .position("서버 개발자")
                .careerYears(5)
                .pricePerReview(60000)
                .build();

        //연관관계 편의 메서드로 스킬과 경력 프로필에 추가
        profile.addSkill(SeniorSkill.builder().skillName("Java").build());
        profile.addSkill(SeniorSkill.builder().skillName("Spring Boot").build());

        profile.addCareer(SeniorCareer.builder()
                .companyName("우아한형제들")
                .position("백엔드 엔지니어")
                .startDate(LocalDate.of(2020, 3, 1))
                .endDate(LocalDate.of(2023, 2, 28))
                .build());
        //when
        SeniorProfile savedProfile = seniorProfileRepository.save(profile);
        em.flush();
        em.clear();
        //then
        SeniorProfile findProfile = seniorProfileRepository.findById(savedProfile.getId())
                .orElseThrow(() -> new IllegalArgumentException("프로필을 찾을 수 없습니다."));

        //profile잘 들어갔는지 확인
        assertThat(findProfile.getCompany()).isEqualTo("토스");

        //skill정보 확인
        assertThat(findProfile.getSkills()).hasSize(2);
        assertThat(findProfile.getSkills())
                .extracting("skillName") // 리스트 안의 객체들에서 skillName 필드만 뽑아냅니다.
                .containsExactlyInAnyOrder("Java", "Spring Boot");

        //career정보 확인
        assertThat(findProfile.getCareers()).hasSize(1);
        assertThat(findProfile.getCareers().get(0).getCompanyName()).isEqualTo("우아한형제들");

    }

}