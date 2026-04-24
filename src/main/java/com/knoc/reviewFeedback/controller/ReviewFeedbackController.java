package com.knoc.reviewFeedback.controller;

import com.knoc.reviewFeedback.dto.ReviewPageDto;
import com.knoc.reviewFeedback.service.ReviewFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Review-Page-Controller", description = "멘토링 후기 공개 페이지")
@Controller
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewFeedbackController {

    private final ReviewFeedbackService reviewFeedbackService;

    @Operation(summary = "멘토링 후기 목록 페이지", description = "전체 후기를 최신순으로 조회합니다.")
    @GetMapping("/posts")
    public String reviews(Model model) {
        ReviewPageDto page = reviewFeedbackService.getReviewPage();
        model.addAttribute("reviews", page.getReviews());
        model.addAttribute("totalCount", page.getTotalCount());
        model.addAttribute("topSeniors", page.getTopSeniors());
        return "review/posts";
    }
}