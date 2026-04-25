package com.knoc.review.controller;

import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import com.knoc.review.dto.ReviewRequestCreateRequest;
import com.knoc.review.dto.ReviewRequestCreateResponse;
import com.knoc.review.service.ReviewRequestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name="Review-Request-Controller",description = "리뷰 요청서 발행 API")
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
@RequestMapping(value = "/reviews")
public class ReviewRequestController {
    private final MemberRepository memberRepository;
    private final ReviewRequestService reviewRequestService;

    @PostMapping(value = "/request")
    public ResponseEntity<ReviewRequestCreateResponse> request(@AuthenticationPrincipal UserDetails userDetails,
                                  @RequestBody @Valid ReviewRequestCreateRequest dto) {
        Member junior = memberRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        ReviewRequestCreateResponse response = reviewRequestService.createReviewRequest(dto, junior.getId());
        return ResponseEntity.ok(response);
    }
}
