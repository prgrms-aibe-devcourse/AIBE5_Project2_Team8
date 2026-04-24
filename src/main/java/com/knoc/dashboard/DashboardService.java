package com.knoc.dashboard;

import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import com.knoc.order.entity.Order;
import com.knoc.order.entity.OrderStatus;
import com.knoc.order.repository.OrderRepository;
import com.knoc.senior.entity.SeniorProfile;
import com.knoc.senior.repository.SeniorProfileRepository;
import com.knoc.reviewFeedback.entity.ReviewFeedback;
import com.knoc.reviewFeedback.repository.ReviewFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final OrderRepository orderRepository;
    private final ReviewFeedbackRepository reviewFeedbackRepository;
    private final MemberRepository memberRepository;
    private final SeniorProfileRepository seniorProfileRepository;


    public JuniorDashboardDto getJuniorDashboard(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        List<Order> orders = orderRepository.findByJunior_IdOrderByCreatedAtDesc(member.getId());

        long pendingCount = orders.stream().filter(o -> o.getStatus() == OrderStatus.PENDING).count();
        long inProgressCount = orders.stream().filter(o -> o.getStatus() == OrderStatus.PAID).count();
        long completedCount = orders.stream().filter(o -> o.getStatus() == OrderStatus.SETTLED).count();

        List<JuniorDashboardDto.OrderSummaryDto> orderSummaryDtos = orders.stream()
                .map(o -> JuniorDashboardDto.OrderSummaryDto.builder()
                        .orderId(o.getId())
                        .seniorNickname(o.getSenior().getNickname())
                        .status(o.getStatus())
                        .hasReview(reviewFeedbackRepository.existsByOrderId(o.getId()))
                        .build())
                .toList();

        return JuniorDashboardDto.builder()
                .id(member.getId())
                .nickname(member.getNickname())
                .profileImageUrl(member.getProfileImageUrl())
                .pendingCount(pendingCount)
                .inProgressCount(inProgressCount)
                .completedCount(completedCount)
                .orders(orderSummaryDtos)
                .build();
    }

    public SeniorDashBoardDto getSeniorDashboard(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        SeniorProfile seniorProfile = seniorProfileRepository.findByMemberId(member.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SENIOR_PROFILE_NOT_FOUND));

        List<Order> orders = orderRepository.findBySenior_IdOrderByCreatedAtDesc(member.getId());

        long pendingCount = orders.stream().filter(o -> o.getStatus() == OrderStatus.PENDING).count();
        long inProgressCount = orders.stream().filter(o -> o.getStatus() == OrderStatus.PAID).count();
        long completedCount = orders.stream().filter(o -> o.getStatus() == OrderStatus.SETTLED).count();

        List<SeniorDashBoardDto.OrderSummaryDto> orderSummaryDtos = orders.stream()
                .map(o -> SeniorDashBoardDto.OrderSummaryDto.builder()
                        .orderId(o.getId())
                        .juniorNickname(o.getJunior().getNickname())
                        .status(o.getStatus())
                        .hasReview(reviewFeedbackRepository.existsByOrderId(o.getId()))
                        .build())
                .toList();

        // 전체 후기에서  별점 분포 계산 로직
        List<ReviewFeedback> allReviews = reviewFeedbackRepository.findBySeniorProfile_Id(seniorProfile.getId());
        Map<Integer, Integer> ratingDistribution = allReviews.stream()
                .collect(Collectors.groupingBy(r -> (int) r.getRating(),
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));

        // 최근 후기 3개
        List<SeniorDashBoardDto.ReviewSummeryDto> reviewSummeryDtos =
                reviewFeedbackRepository.findTop3BySeniorProfile_IdOrderByCreatedAtDesc(seniorProfile.getId())
                        .stream()
                        .map(r -> SeniorDashBoardDto.ReviewSummeryDto.builder()
                                .reviewId(r.getId())
                                .reviewerNickname(r.getJunior().getNickname())
                                .rating(r.getRating())
                                .comment(r.getComment())
                                .createdAt(r.getCreatedAt())
                                .build())
                        .toList();

        // 기술 스택 이름 목록
        List<String> skills = seniorProfile.getSkills().stream()
                .map(s -> s.getSkillName())
                .toList();

        return SeniorDashBoardDto.builder()
                .id(member.getId())
                .nickname(member.getNickname())
                .profileImageUrl(member.getProfileImageUrl())
                .pendingCount(pendingCount)
                .inProgressCount(inProgressCount)
                .completedCount(completedCount)
                .company(seniorProfile.getCompany())
                .careerYears(seniorProfile.getCareerYears())
                .linkedinUrl(seniorProfile.getLinkedinUrl())
                .skills(skills)
                .averageRating(seniorProfile.getAvgRating())
                .reviewCount(seniorProfile.getTotalReviewCount())
                .ratingDistribution(ratingDistribution)
                .orders(orderSummaryDtos)
                .reviews(reviewSummeryDtos)
                .aiInsights(null)
                .build();
    }
    public SeniorReviewPageDto getSeniorReviews(String email, int pageNumber) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        SeniorProfile profile = seniorProfileRepository.findByMemberId(member.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SENIOR_PROFILE_NOT_FOUND));

        Page<ReviewFeedback> reviewPage = reviewFeedbackRepository
                .findBySeniorProfile_IdOrderByCreatedAtDesc(
                        profile.getId(), PageRequest.of(pageNumber, 10));

        List<SeniorDashBoardDto.ReviewSummeryDto> reviews = reviewPage.getContent().stream()
                .map(r -> SeniorDashBoardDto.ReviewSummeryDto.builder()
                        .reviewId(r.getId())
                        .reviewerNickname(r.getJunior().getNickname())
                        .rating(r.getRating())
                        .comment(r.getComment())
                        .createdAt(r.getCreatedAt())
                        .build())
                .toList();

        return SeniorReviewPageDto.builder()
                .nickname(member.getNickname())
                .averageRating(profile.getAvgRating())
                .reviewCount(profile.getTotalReviewCount())
                .reviews(reviews)
                .currentPage(reviewPage.getNumber())
                .totalPages(reviewPage.getTotalPages())
                .hasPrevious(reviewPage.hasPrevious())
                .hasNext(reviewPage.hasNext())
                .build();
    }
}