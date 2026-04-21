package com.knoc.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/my")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;


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


}
