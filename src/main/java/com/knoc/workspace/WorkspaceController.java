package com.knoc.workspace;

import com.knoc.chat.dto.ChatMessageResponse;
import com.knoc.chat.service.ChatMessageService;
import com.knoc.reviewFeedback.dto.ReviewFeedbackRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@Tag(name = "Workspace Controller", description = "워크스페이스(주문별 화면) 조회, 채팅 내역, 코드 리뷰 리포트, 멘토링 종료(정산), 주니어 후기")
@Controller
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceFacadeService workspaceFacadeService;
    private final ChatMessageService chatMessageService;

    @Operation(summary = "워크스페이스 페이지 조회", description = "해당 주문(orderId)에 대한 워크스페이스 화면을 렌더링합니다.")
    @GetMapping("/orders/{orderId}")
    public String workspace(@PathVariable Long orderId,
                            @RequestParam(value = "reviewOnly", required = false) String reviewOnly,
                            Principal principal,
                            Model model) {
        WorkspaceDto dto = workspaceFacadeService.getWorkspaceData(orderId, principal.getName());
        model.addAttribute("workspace", dto);
        model.addAttribute("openReviewOnlyModal", "1".equals(reviewOnly) || "true".equalsIgnoreCase(reviewOnly));
        return "workspace/workspace";
    }

    @Operation(summary = "채팅 메시지 이전 내역 조회", description = "워크스페이스 내 채팅의 이전 메시지를 페이지네이션 형태로 가져옵니다.")
    @GetMapping("/orders/{orderId}/messages")
    @ResponseBody
    public List<ChatMessageResponse> loadMore(@PathVariable Long orderId,
                                              @RequestParam Long before,
                                              Principal principal) {
        Long chatRoomId = workspaceFacadeService.resolveVerifiedChatRoomId(orderId, principal.getName());
        return chatMessageService.getPreviousMessages(chatRoomId, before, principal.getName());
    }

    @Operation(summary = "코드 리뷰 리포트 제출", description = "시니어가 해당 주문의 코드 리뷰 리포트를 제출합니다.")
    @PostMapping("/orders/{orderId}/report")
    @ResponseBody
    public ResponseEntity<Void> submitReport(@PathVariable Long orderId,
                                             @RequestParam String industryPerspective,
                                             @RequestParam String edgeCases,
                                             @RequestParam String alternatives,
                                             Principal principal) {
        workspaceFacadeService.submitReport(orderId, principal.getName(),
                industryPerspective, edgeCases, alternatives);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "주문 정산 처리", description = "해당 주문을 완료하고 정산 상태로 변경합니다.")
    @PostMapping("/orders/{orderId}/settle")
    @ResponseBody
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> settleOrder(@PathVariable Long orderId, Principal principal) {
        workspaceFacadeService.settleOrder(orderId, principal.getName());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "후기 제출", description = "멘토링 종료 후 주니어가 후기를 제출합니다.")
    @PostMapping("/orders/{orderId}/feedback")
    @ResponseBody
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> submitFeedback(@PathVariable Long orderId,
                                               @RequestBody ReviewFeedbackRequestDto dto,
                                               Principal principal) {
        workspaceFacadeService.submitFeedback(orderId, principal.getName(), dto);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "후기 조회", description = "제출된 후기(평점/코멘트) 정보를 조회합니다.")
    @GetMapping("/orders/{orderId}/feedback")
    @ResponseBody
    public ReviewFeedbackResponse getReview(@PathVariable Long orderId, Principal principal) {
        return workspaceFacadeService.getReviewFeedback(orderId, principal.getName());
    }

    @Operation(summary = "코드 리뷰 리포트 수정", description = "이미 제출된 코드 리뷰 리포트의 내용을 수정합니다.")
    @PatchMapping("/orders/{orderId}/report")
    @ResponseBody
    public ResponseEntity<Void> updateReport(@PathVariable Long orderId,
                                             @RequestParam String industryPerspective,
                                             @RequestParam String edgeCases,
                                             @RequestParam String alternatives,
                                             Principal principal) {
        workspaceFacadeService.updateReport(orderId, principal.getName(),
                industryPerspective, edgeCases, alternatives);
        return ResponseEntity.ok().build();
    }
}