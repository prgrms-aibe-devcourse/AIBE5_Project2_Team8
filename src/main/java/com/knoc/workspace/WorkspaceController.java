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

@Tag(name = "Workspace Controller", description = "주문/작업 공간 관련 기능(워크스페이스 조회, 보고서 작성, 정산, 피드백)")
@Controller
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceFacadeService workspaceFacadeService;
    private final ChatMessageService chatMessageService;

    @Operation(summary = "워크스페이스 페이지 조회", description = "해당 주문(orderId)에 대한 워크스페이스 화면을 렌더링합니다.")
    @GetMapping("/orders/{orderId}")
    public String workspace(@PathVariable Long orderId, Principal principal, Model model) {
        WorkspaceDto dto = workspaceFacadeService.getWorkspaceData(orderId, principal.getName());
        model.addAttribute("workspace", dto);
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

    @Operation(summary = "작업 보고서 제출", description = "시니어가 작업한 보고서를 제출합니다.")
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

    @Operation(summary = "리뷰/피드백 제출", description = "작업 완료 후 리뷰 및 피드백을 제출합니다.")
    @PostMapping("/orders/{orderId}/feedback")
    @ResponseBody
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> submitFeedback(@PathVariable Long orderId,
                                               @RequestBody ReviewFeedbackRequestDto dto,
                                               Principal principal) {
        workspaceFacadeService.submitFeedback(orderId, principal.getName(), dto);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "리뷰 정보 조회", description = "제출된 리뷰 및 피드백 정보를 조회합니다.")
    @GetMapping("/orders/{orderId}/review")
    @ResponseBody
    public ReviewFeedbackResponse getReview(@PathVariable Long orderId, Principal principal) {
        return workspaceFacadeService.getReviewFeedback(orderId, principal.getName());
    }

    @Operation(summary = "작업 보고서 수정", description = "이미 제출된 작업 보고서의 내용을 수정합니다.")
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