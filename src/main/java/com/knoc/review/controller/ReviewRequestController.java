package com.knoc.review.controller;

import com.knoc.review.dto.ReviewRequestCreateRequest;
import com.knoc.review.dto.ReviewRequestCreateResponse;
import com.knoc.review.dto.ReviewRequestUpdateRequest;
import com.knoc.review.service.ReviewRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@Tag(name="Review-Request-Controller",description = "리뷰 요청서 발행 API")
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
@RequestMapping(value = "/reviews")
public class ReviewRequestController {
    private final ReviewRequestService reviewRequestService;

    @Operation(summary = "리뷰 요청서 생성", description = "주니어가 결제 완료된 주문에 대해 리뷰 요청서를 생성합니다.")
    @PostMapping(value = "/request")
    public ResponseEntity<ReviewRequestCreateResponse> request(@AuthenticationPrincipal UserDetails userDetails,
                                  @RequestBody @Valid ReviewRequestCreateRequest dto) {
        ReviewRequestCreateResponse response = reviewRequestService.createReviewRequest(dto, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "리뷰 요청서 수정", description = "주니어가 리포트 작성 전까지 리뷰 요청서(PR 링크/요청 정보)를 수정합니다.")
    @PatchMapping("/request")
    @PreAuthorize("hasRole('JUNIOR')")
    public ResponseEntity<Map<String, Long>> update(@AuthenticationPrincipal UserDetails userDetails,
                                                    @RequestBody @Valid ReviewRequestUpdateRequest req) {
        Long orderId = reviewRequestService.updateReviewRequest(userDetails.getUsername(), req);
        return ResponseEntity.ok(Map.of("orderId", orderId));
    }
}
