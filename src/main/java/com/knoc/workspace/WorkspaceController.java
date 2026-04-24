package com.knoc.workspace;

import com.knoc.chat.dto.ChatMessageResponse;
import com.knoc.chat.service.ChatMessageService;
import com.knoc.reviewFeedback.dto.ReviewFeedbackRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceFacadeService workspaceFacadeService;
    private final ChatMessageService chatMessageService;

    @GetMapping("/orders/{orderId}")
    public String workspace(@PathVariable Long orderId, Principal principal, Model model) {
        WorkspaceDto dto = workspaceFacadeService.getWorkspaceData(orderId, principal.getName());
        model.addAttribute("workspace", dto);
        return "workspace/workspace";
    }

    @GetMapping("/orders/{orderId}/messages")
    @ResponseBody
    public List<ChatMessageResponse> loadMore(@PathVariable Long orderId,
                                              @RequestParam Long before,
                                              Principal principal) {
        Long chatRoomId = workspaceFacadeService.resolveVerifiedChatRoomId(orderId, principal.getName());
        return chatMessageService.getPreviousMessages(chatRoomId, before, principal.getName());
    }

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

    @PostMapping("/orders/{orderId}/settle")
    @ResponseBody
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> settleOrder(@PathVariable Long orderId, Principal principal) {
        workspaceFacadeService.settleOrder(orderId, principal.getName());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/orders/{orderId}/feedback")
    @ResponseBody
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> submitFeedback(@PathVariable Long orderId,
                                               @RequestBody ReviewFeedbackRequestDto dto,
                                               Principal principal) {
        workspaceFacadeService.submitFeedback(orderId, principal.getName(), dto);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/orders/{orderId}/review")
    @ResponseBody
    public ReviewFeedbackResponse getReview(@PathVariable Long orderId, Principal principal) {
        return workspaceFacadeService.getReviewFeedback(orderId, principal.getName());
    }

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