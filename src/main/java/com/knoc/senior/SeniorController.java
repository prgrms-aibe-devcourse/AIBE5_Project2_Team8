package com.knoc.senior;

import com.knoc.senior.dto.SeniorProfileRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/senior/profile")
public class SeniorController {
    private final SeniorProfileService seniorProfileService;

    @GetMapping("new")
    public String createProfileForm(Model model) {
        model.addAttribute("profileDto", new SeniorProfileRequestDto());
        return "senior/profile_form";
    }
}



