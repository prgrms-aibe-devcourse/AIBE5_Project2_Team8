package com.knoc.dashboard;

import com.knoc.chat.entity.ChatRoom;
import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import com.knoc.order.entity.Order;
import com.knoc.order.entity.OrderStatus;
import com.knoc.order.repository.OrderRepository;
import com.knoc.senior.entity.SeniorProfile;
import com.knoc.senior.entity.SeniorSkill;
import com.knoc.senior.repository.SeniorProfileRepository;
import com.knoc.settlement.entity.ReviewFeedback;
import com.knoc.settlement.repository.ReviewFeedbackRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @InjectMocks
    private DashboardService dashboardService;

    @Mock private MemberRepository memberRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ReviewFeedbackRepository reviewFeedbackRepository;
    @Mock private SeniorProfileRepository seniorProfileRepository;

    // ===== getJuniorDashboard =====

    @Test
    @DisplayName("주니어 대시보드 조회 성공: 주문 상태별 카운트와 목록이 올바르게 반환")
    void getJuniorDashboard_Success() {
        // given
        String email = "junior@knoc.com";

        Member junior = mock(Member.class);
        given(junior.getId()).willReturn(1L);
        given(junior.getNickname()).willReturn("코딩초보");
        given(memberRepository.findByEmail(email)).willReturn(Optional.of(junior));

        Member seniorMember = mock(Member.class);
        given(seniorMember.getNickname()).willReturn("백엔드고수");

        // PENDING 주문
        Order pendingOrder = Order.builder()
                .chatRoom(mock(ChatRoom.class))
                .junior(junior)
                .senior(seniorMember)
                .amount(55000)
                .build();
        ReflectionTestUtils.setField(pendingOrder, "id", 1L);

        // PAID 주문
        Order paidOrder = Order.builder()
                .chatRoom(mock(ChatRoom.class))
                .junior(junior)
                .senior(seniorMember)
                .amount(55000)
                .build();
        paidOrder.updateStatus(OrderStatus.PAID);
        ReflectionTestUtils.setField(paidOrder, "id", 2L);

        // SETTLED 주문 (후기 있음)
        Order settledOrder = Order.builder()
                .chatRoom(mock(ChatRoom.class))
                .junior(junior)
                .senior(seniorMember)
                .amount(55000)
                .build();
        settledOrder.updateStatus(OrderStatus.PAID);
        settledOrder.updateStatus(OrderStatus.SETTLED);
        ReflectionTestUtils.setField(settledOrder, "id", 3L);

        given(orderRepository.findByJunior_IdOrderByCreatedAtDesc(1L))
                .willReturn(List.of(settledOrder, paidOrder, pendingOrder));
        given(reviewFeedbackRepository.existsByOrderId(1L)).willReturn(false);
        given(reviewFeedbackRepository.existsByOrderId(2L)).willReturn(false);
        given(reviewFeedbackRepository.existsByOrderId(3L)).willReturn(true);

        // when
        JuniorDashboardDto result = dashboardService.getJuniorDashboard(email);

        // then
        assertThat(result.getNickname()).isEqualTo("코딩초보");
        assertThat(result.getPendingCount()).isEqualTo(1);
        assertThat(result.getInProgressCount()).isEqualTo(1);
        assertThat(result.getCompletedCount()).isEqualTo(1);
        assertThat(result.getOrders()).hasSize(3);

        JuniorDashboardDto.OrderSummaryDto settledDto = result.getOrders().get(0);
        assertThat(settledDto.getStatus()).isEqualTo(OrderStatus.SETTLED);
        assertThat(settledDto.isHasReview()).isTrue();

        JuniorDashboardDto.OrderSummaryDto pendingDto = result.getOrders().get(2);
        assertThat(pendingDto.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(pendingDto.isHasReview()).isFalse();
    }

    @Test
    @DisplayName("주니어 대시보드 조회 성공: 주문이 없으면 카운트 0, 빈 목록이 반환")
    void getJuniorDashboard_Success_NoOrders() {
        // given
        String email = "junior@knoc.com";

        Member junior = mock(Member.class);
        given(junior.getId()).willReturn(1L);
        given(junior.getNickname()).willReturn("코딩초보");
        given(memberRepository.findByEmail(email)).willReturn(Optional.of(junior));
        given(orderRepository.findByJunior_IdOrderByCreatedAtDesc(1L)).willReturn(List.of());

        // when
        JuniorDashboardDto result = dashboardService.getJuniorDashboard(email);

        // then
        assertThat(result.getPendingCount()).isZero();
        assertThat(result.getInProgressCount()).isZero();
        assertThat(result.getCompletedCount()).isZero();
        assertThat(result.getOrders()).isEmpty();
    }

    @Test
    @DisplayName("주니어 대시보드 조회 실패: 존재하지 않는 이메일이면 MEMBER_NOT_FOUND 예외가 발생")
    void getJuniorDashboard_Fail_MemberNotFound() {
        // given
        given(memberRepository.findByEmail("none@knoc.com")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> dashboardService.getJuniorDashboard("none@knoc.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.MEMBER_NOT_FOUND.getMessage());
    }

    // ===== getSeniorDashboard =====

    @Test
    @DisplayName("시니어 대시보드 조회 성공: 프로필, 주문, 후기, 별점 분포가 올바르게 반환")
    void getSeniorDashboard_Success() {
        // given
        String email = "senior@knoc.com";

        Member seniorMember = mock(Member.class);
        given(seniorMember.getId()).willReturn(2L);
        given(seniorMember.getNickname()).willReturn("백엔드고수");
        given(memberRepository.findByEmail(email)).willReturn(Optional.of(seniorMember));

        SeniorSkill skill = SeniorSkill.builder()
                .seniorProfile(mock(SeniorProfile.class))
                .skillName("Java")
                .build();

        SeniorProfile profile = mock(SeniorProfile.class);
        given(profile.getId()).willReturn(10L);
        given(profile.getCompany()).willReturn("카카오");
        given(profile.getCareerYears()).willReturn(7);
        given(profile.getLinkedinUrl()).willReturn("https://linkedin.com/in/test");
        given(profile.getAvgRating()).willReturn(new BigDecimal("4.5"));
        given(profile.getTotalReviewCount()).willReturn(2);
        given(profile.getSkills()).willReturn(List.of(skill));
        given(seniorProfileRepository.findByMemberId(2L)).willReturn(Optional.of(profile));

        Member juniorMember = mock(Member.class);
        given(juniorMember.getNickname()).willReturn("코딩초보");

        // SETTLED 주문
        Order settledOrder = Order.builder()
                .chatRoom(mock(ChatRoom.class))
                .junior(juniorMember)
                .senior(seniorMember)
                .amount(55000)
                .build();
        settledOrder.updateStatus(OrderStatus.PAID);
        settledOrder.updateStatus(OrderStatus.SETTLED);
        ReflectionTestUtils.setField(settledOrder, "id", 1L);

        // PAID 주문
        Order paidOrder = Order.builder()
                .chatRoom(mock(ChatRoom.class))
                .junior(juniorMember)
                .senior(seniorMember)
                .amount(55000)
                .build();
        paidOrder.updateStatus(OrderStatus.PAID);
        ReflectionTestUtils.setField(paidOrder, "id", 2L);

        given(orderRepository.findBySenior_IdOrderByCreatedAtDesc(2L))
                .willReturn(List.of(settledOrder, paidOrder));
        given(reviewFeedbackRepository.existsByOrderId(1L)).willReturn(true);
        given(reviewFeedbackRepository.existsByOrderId(2L)).willReturn(false);

        // 별점 분포용 전체 후기
        ReviewFeedback review5 = mock(ReviewFeedback.class);
        given(review5.getRating()).willReturn((byte) 5);
        ReviewFeedback review4 = mock(ReviewFeedback.class);
        given(review4.getRating()).willReturn((byte) 4);
        given(reviewFeedbackRepository.findBySeniorProfile_Id(10L))
                .willReturn(List.of(review5, review4));

        // 최근 후기 3개
        Member reviewer = mock(Member.class);
        given(reviewer.getNickname()).willReturn("자바러버");

        ReviewFeedback top3Review = mock(ReviewFeedback.class);
        given(top3Review.getId()).willReturn(100L);
        given(top3Review.getJunior()).willReturn(reviewer);
        given(top3Review.getRating()).willReturn((byte) 5);
        given(top3Review.getComment()).willReturn("정말 도움이 됐어요!");
        given(top3Review.getCreatedAt()).willReturn(LocalDateTime.of(2025, 4, 1, 12, 0));

        given(reviewFeedbackRepository.findTop3BySeniorProfile_IdOrderByCreatedAtDesc(10L))
                .willReturn(List.of(top3Review));

        // when
        SeniorDashBoardDto result = dashboardService.getSeniorDashboard(email);

        // then
        assertThat(result.getNickname()).isEqualTo("백엔드고수");
        assertThat(result.getCompany()).isEqualTo("카카오");
        assertThat(result.getCareerYears()).isEqualTo(7);
        assertThat(result.getSkills()).containsExactly("Java");
        assertThat(result.getAverageRating()).isEqualByComparingTo(new BigDecimal("4.5"));
        assertThat(result.getReviewCount()).isEqualTo(2);

        assertThat(result.getPendingCount()).isZero();
        assertThat(result.getInProgressCount()).isEqualTo(1);
        assertThat(result.getCompletedCount()).isEqualTo(1);
        assertThat(result.getOrders()).hasSize(2);

        // 별점 분포: 5점 1개, 4점 1개
        assertThat(result.getRatingDistribution()).containsEntry(5, 1).containsEntry(4, 1);

        // 후기
        assertThat(result.getReviews()).hasSize(1);
        SeniorDashBoardDto.ReviewSummeryDto reviewDto = result.getReviews().get(0);
        assertThat(reviewDto.getReviewerNickname()).isEqualTo("자바러버");
        assertThat(reviewDto.getRating()).isEqualTo(5);
        assertThat(reviewDto.getComment()).isEqualTo("정말 도움이 됐어요!");
        assertThat(reviewDto.getCreatedAt()).isEqualTo(LocalDateTime.of(2025, 4, 1, 12, 0));
    }

    @Test
    @DisplayName("시니어 대시보드 조회 성공: 후기가 없으면 별점 분포는 빈 Map, 후기 목록은 빈 리스트")
    void getSeniorDashboard_Success_NoReviews() {
        // given
        String email = "senior@knoc.com";

        Member seniorMember = mock(Member.class);
        given(seniorMember.getId()).willReturn(2L);
        given(seniorMember.getNickname()).willReturn("백엔드고수");
        given(memberRepository.findByEmail(email)).willReturn(Optional.of(seniorMember));

        SeniorProfile profile = mock(SeniorProfile.class);
        given(profile.getId()).willReturn(10L);
        given(profile.getCompany()).willReturn("카카오");
        given(profile.getCareerYears()).willReturn(7);
        given(profile.getLinkedinUrl()).willReturn(null);
        given(profile.getAvgRating()).willReturn(BigDecimal.ZERO);
        given(profile.getTotalReviewCount()).willReturn(0);
        given(profile.getSkills()).willReturn(List.of());
        given(seniorProfileRepository.findByMemberId(2L)).willReturn(Optional.of(profile));

        given(orderRepository.findBySenior_IdOrderByCreatedAtDesc(2L)).willReturn(List.of());
        given(reviewFeedbackRepository.findBySeniorProfile_Id(10L)).willReturn(List.of());
        given(reviewFeedbackRepository.findTop3BySeniorProfile_IdOrderByCreatedAtDesc(10L)).willReturn(List.of());

        // when
        SeniorDashBoardDto result = dashboardService.getSeniorDashboard(email);

        // then
        assertThat(result.getOrders()).isEmpty();
        assertThat(result.getReviews()).isEmpty();
        assertThat(result.getRatingDistribution()).isEmpty();
        assertThat(result.getSkills()).isEmpty();
    }

    @Test
    @DisplayName("시니어 대시보드 조회 실패: 존재하지 않는 이메일이면 MEMBER_NOT_FOUND 예외가 발생")
    void getSeniorDashboard_Fail_MemberNotFound() {
        // given
        given(memberRepository.findByEmail("none@knoc.com")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> dashboardService.getSeniorDashboard("none@knoc.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.MEMBER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("시니어 대시보드 조회 실패: 시니어 프로필이 없으면 SENIOR_PROFILE_NOT_FOUND 예외가 발생한다")
    void getSeniorDashboard_Fail_SeniorProfileNotFound() {
        // given
        String email = "senior@knoc.com";

        Member seniorMember = mock(Member.class);
        given(seniorMember.getId()).willReturn(2L);
        given(memberRepository.findByEmail(email)).willReturn(Optional.of(seniorMember));
        given(seniorProfileRepository.findByMemberId(2L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> dashboardService.getSeniorDashboard(email))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.SENIOR_PROFILE_NOT_FOUND.getMessage());
    }

    // ===== getSeniorReviews =====

    @Test
    @DisplayName("후기 전체 조회 성공: 닉네임·평균평점·총개수·후기 목록이 올바르게 반환된다")
    void getSeniorReviews_Success() {
        // given
        String email = "senior@knoc.com";

        Member seniorMember = mock(Member.class);
        given(seniorMember.getId()).willReturn(2L);
        given(seniorMember.getNickname()).willReturn("백엔드고수");
        given(memberRepository.findByEmail(email)).willReturn(Optional.of(seniorMember));

        SeniorProfile profile = mock(SeniorProfile.class);
        given(profile.getId()).willReturn(10L);
        given(profile.getAvgRating()).willReturn(new BigDecimal("4.5"));
        given(profile.getTotalReviewCount()).willReturn(2);
        given(seniorProfileRepository.findByMemberId(2L)).willReturn(Optional.of(profile));

        Member reviewer1 = mock(Member.class);
        given(reviewer1.getNickname()).willReturn("코딩초보");
        Member reviewer2 = mock(Member.class);
        given(reviewer2.getNickname()).willReturn("자바러버");

        ReviewFeedback review1 = mock(ReviewFeedback.class);
        given(review1.getId()).willReturn(1L);
        given(review1.getJunior()).willReturn(reviewer1);
        given(review1.getRating()).willReturn((byte) 5);
        given(review1.getComment()).willReturn("정말 도움이 됐어요!");
        given(review1.getCreatedAt()).willReturn(LocalDateTime.of(2025, 4, 10, 12, 0));

        ReviewFeedback review2 = mock(ReviewFeedback.class);
        given(review2.getId()).willReturn(2L);
        given(review2.getJunior()).willReturn(reviewer2);
        given(review2.getRating()).willReturn((byte) 4);
        given(review2.getComment()).willReturn("리뷰가 꼼꼼했어요.");
        given(review2.getCreatedAt()).willReturn(LocalDateTime.of(2025, 3, 20, 9, 0));

        given(reviewFeedbackRepository.findBySeniorProfile_IdOrderByCreatedAtDesc(10L))
                .willReturn(List.of(review1, review2));

        // when
        SeniorReviewPageDto result = dashboardService.getSeniorReviews(email);

        // then
        assertThat(result.getNickname()).isEqualTo("백엔드고수");
        assertThat(result.getAverageRating()).isEqualByComparingTo(new BigDecimal("4.5"));
        assertThat(result.getReviewCount()).isEqualTo(2);
        assertThat(result.getReviews()).hasSize(2);

        SeniorDashBoardDto.ReviewSummeryDto first = result.getReviews().get(0);
        assertThat(first.getReviewerNickname()).isEqualTo("코딩초보");
        assertThat(first.getRating()).isEqualTo(5);
        assertThat(first.getComment()).isEqualTo("정말 도움이 됐어요!");
        assertThat(first.getCreatedAt()).isEqualTo(LocalDateTime.of(2025, 4, 10, 12, 0));
    }

    @Test
    @DisplayName("후기 전체 조회 성공: 후기가 없으면 빈 목록이 반환된다")
    void getSeniorReviews_Success_NoReviews() {
        // given
        String email = "senior@knoc.com";

        Member seniorMember = mock(Member.class);
        given(seniorMember.getId()).willReturn(2L);
        given(seniorMember.getNickname()).willReturn("백엔드고수");
        given(memberRepository.findByEmail(email)).willReturn(Optional.of(seniorMember));

        SeniorProfile profile = mock(SeniorProfile.class);
        given(profile.getId()).willReturn(10L);
        given(profile.getAvgRating()).willReturn(BigDecimal.ZERO);
        given(profile.getTotalReviewCount()).willReturn(0);
        given(seniorProfileRepository.findByMemberId(2L)).willReturn(Optional.of(profile));

        given(reviewFeedbackRepository.findBySeniorProfile_IdOrderByCreatedAtDesc(10L))
                .willReturn(List.of());

        // when
        SeniorReviewPageDto result = dashboardService.getSeniorReviews(email);

        // then
        assertThat(result.getReviews()).isEmpty();
        assertThat(result.getReviewCount()).isZero();
    }

    @Test
    @DisplayName("후기 전체 조회 실패: 존재하지 않는 이메일이면 MEMBER_NOT_FOUND 예외가 발생한다")
    void getSeniorReviews_Fail_MemberNotFound() {
        // given
        given(memberRepository.findByEmail("none@knoc.com")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> dashboardService.getSeniorReviews("none@knoc.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.MEMBER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("후기 전체 조회 실패: 시니어 프로필이 없으면 SENIOR_PROFILE_NOT_FOUND 예외가 발생한다")
    void getSeniorReviews_Fail_SeniorProfileNotFound() {
        // given
        String email = "senior@knoc.com";

        Member seniorMember = mock(Member.class);
        given(seniorMember.getId()).willReturn(2L);
        given(memberRepository.findByEmail(email)).willReturn(Optional.of(seniorMember));
        given(seniorProfileRepository.findByMemberId(2L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> dashboardService.getSeniorReviews(email))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.SENIOR_PROFILE_NOT_FOUND.getMessage());
    }
}