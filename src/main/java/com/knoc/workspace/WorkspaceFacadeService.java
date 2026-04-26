package com.knoc.workspace;

import com.knoc.chat.dto.ChatMessageResponse;
import com.knoc.chat.entity.ChatRoomStatus;
import com.knoc.chat.entity.ChatSystemEvent;
import com.knoc.chat.entity.MessageType;
import com.knoc.chat.service.ChatMessageService;
import com.knoc.github.dto.GithubPrMetadata;
import com.knoc.github.service.GithubApiService;
import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import com.knoc.member.MemberRole;
import com.knoc.order.entity.Order;
import com.knoc.order.entity.OrderStatus;
import com.knoc.order.repository.OrderRepository;
import com.knoc.order.service.OrderService;
import com.knoc.review.entity.ReviewReport;
import com.knoc.review.entity.ReviewRequest;
import com.knoc.review.repository.ReviewReportRepository;
import com.knoc.review.repository.ReviewRequestRepository;
import com.knoc.reviewFeedback.dto.ReviewFeedbackRequestDto;
import com.knoc.reviewFeedback.entity.ReviewFeedback;
import com.knoc.reviewFeedback.repository.ReviewFeedbackRepository;
import com.knoc.reviewFeedback.service.ReviewFeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WorkspaceFacadeService {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final ReviewRequestRepository reviewRequestRepository;
    private final ReviewReportRepository reviewReportRepository;
    private final ReviewFeedbackRepository reviewFeedbackRepository;
    private final ReviewFeedbackService reviewFeedbackService;
    private final GithubApiService githubApiService;
    private final ChatMessageService chatMessageService;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Long resolveVerifiedChatRoomId(Long orderId, String email) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        boolean isJunior = order.getJunior().getId().equals(member.getId());
        boolean isSenior = order.getSenior().getId().equals(member.getId());
        if (!isJunior && !isSenior) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return order.getChatRoom().getId();
    }

    @Transactional(readOnly = true)
    public WorkspaceDto getWorkspaceData(Long orderId, String email) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        boolean isJunior = order.getJunior().getId().equals(member.getId());
        boolean isSenior = order.getSenior().getId().equals(member.getId());
        if (!isJunior && !isSenior) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        Member opponent = isJunior ? order.getSenior() : order.getJunior();
        Long chatRoomId = order.getChatRoom().getId();
        boolean chatRoomActive = order.getChatRoom().getStatus() == ChatRoomStatus.ACTIVE;

        List<ChatMessageResponse> initialMessages =
                chatMessageService.getPreviousMessages(chatRoomId, Long.MAX_VALUE, email);

        Optional<ReviewRequest> reviewRequestOpt = reviewRequestRepository.findByOrder(order);
        String githubPrUrl = reviewRequestOpt.map(ReviewRequest::getGithubPrUrl).orElse(null);
        String projectContext = reviewRequestOpt.map(ReviewRequest::getProjectContext).orElse(null);
        String concernPoint = reviewRequestOpt.map(ReviewRequest::getConcernPoint).orElse(null);

        GithubPrMetadata prMetadata = null;
        if (githubPrUrl != null) {
            try {
                prMetadata = githubApiService.fetchPrMetadata(githubPrUrl);
            } catch (Exception ignored) {
            }
        }

        String industryPerspective = null, edgeCases = null, alternatives = null, aiSummary = null;
        boolean hasReport = false;
        if (reviewRequestOpt.isPresent()) {
            Optional<ReviewReport> reportOpt =
                    reviewReportRepository.findByReviewRequest(reviewRequestOpt.get());
            if (reportOpt.isPresent()) {
                ReviewReport report = reportOpt.get();
                industryPerspective = report.getIndustryPerspective();
                edgeCases = report.getEdgeCases();
                alternatives = report.getAlternatives();
                aiSummary = report.getAiSummary();
                hasReport = true;
            }
        }

        boolean hasReview = reviewFeedbackRepository.existsByOrderId(orderId);

        return WorkspaceDto.builder()
                .orderId(orderId)
                .orderStatus(order.getStatus().name())
                .chatRoomId(chatRoomId)
                .chatRoomActive(chatRoomActive)
                .currentNickname(member.getNickname())
                .opponentNickname(opponent.getNickname())
                .opponentAvatarUrl(opponent.getProfileImageUrl())
                .initialMessages(initialMessages)
                .githubPrUrl(githubPrUrl)
                .projectContext(projectContext)
                .concernPoint(concernPoint)
                .prMetadata(prMetadata)
                .reportIndustryPerspective(industryPerspective)
                .reportEdgeCases(edgeCases)
                .reportAlternatives(alternatives)
                .reportAiSummary(aiSummary)
                .hasReport(hasReport)
                .hasReview(hasReview)
                .isSenior(isSenior)
                .build();
    }

    @Transactional
    public void submitReport(Long orderId, String email,
                             String industryPerspective, String edgeCases, String alternatives) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (!order.getSenior().getId().equals(member.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        ReviewRequest reviewRequest = reviewRequestRepository.findByOrder(order)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE));

        if (reviewReportRepository.findByReviewRequest(reviewRequest).isPresent()) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        reviewReportRepository.save(ReviewReport.builder()
                .reviewRequest(reviewRequest)
                .senior(member)
                .industryPerspective(industryPerspective)
                .edgeCases(edgeCases)
                .alternatives(alternatives)
                .build());

        eventPublisher.publishEvent(new ChatSystemEvent(
                order.getChatRoom().getId(),
                MessageType.REPORT_COMPLETED,
                null,
                orderId
        ));
    }

    @Transactional
    public void updateReport(Long orderId, String email,
                             String industryPerspective, String edgeCases, String alternatives) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (!order.getSenior().getId().equals(member.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        if (order.getStatus() == OrderStatus.SETTLED) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        ReviewRequest reviewRequest = reviewRequestRepository.findByOrder(order)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE));

        ReviewReport report = reviewReportRepository.findByReviewRequest(reviewRequest)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE));

        report.update(industryPerspective, edgeCases, alternatives);
    }

    @Transactional
    public void settleOrder(Long orderId, String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        orderService.settleOrder(orderId, member.getId());
    }

    @Transactional
    public void submitFeedback(Long orderId, String email, ReviewFeedbackRequestDto dto) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        reviewFeedbackService.createReview(dto, member.getId());

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        eventPublisher.publishEvent(new ChatSystemEvent(
                order.getChatRoom().getId(),
                MessageType.REVIEW_WRITTEN,
                null,
                orderId
        ));
    }

    @Transactional(readOnly = true)
    public ReviewFeedbackResponse getReviewFeedback(Long orderId, String email) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        boolean isJunior = order.getJunior().getId().equals(member.getId());
        boolean isSenior = order.getSenior().getId().equals(member.getId());
        if (!isJunior && !isSenior) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        ReviewFeedback feedback = reviewFeedbackRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE));
        return new ReviewFeedbackResponse(feedback.getRating(), feedback.getComment());
    }
}