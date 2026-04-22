package com.knoc.dashboard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name="Dashboard-Controller",description = "내 멘토링(dashboard) 관련 페이지")
@Controller
@RequestMapping("/my")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;


    @Operation(summary = "대시보드 페이지 조회",description = "로그인한 사용자의 역할(주니어/시니어)에 따라 각각의 내 멘토링 페이지를 반환합니다.")
    @GetMapping("/dashboard")
    public String redirect(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        String email = userDetails.getUsername();
        boolean isSenior = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SENIOR"));
        if (!isSenior) {
            model.addAttribute("junior", dashboardService.getJuniorDashboard(email));
            return "my/dashboard/junior";
        } else {
            model.addAttribute("senior", dashboardService.getSeniorDashboard(email));
            return "my/dashboard/senior";
        }
    }

    @Operation(summary = "받은 후기 전체 조회", description = "시니어가 받은 후기를 페이지 단위로 반환합니다.")
    @GetMapping("/reviews")
    public String reviews(@AuthenticationPrincipal UserDetails userDetails,
                          @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int p,
                          Model model) {
        model.addAttribute("page", dashboardService.getSeniorReviews(userDetails.getUsername(), p));
        return "my/dashboard/reviews";
    }
}
