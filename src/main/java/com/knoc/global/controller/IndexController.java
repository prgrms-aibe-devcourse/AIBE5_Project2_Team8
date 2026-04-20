package com.knoc.global.controller;

import com.knoc.senior.SeniorProfileService;
import com.knoc.senior.dto.SeniorSearchCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class IndexController {

    private static final List<String> POPULAR_SKILLS =
            List.of("Java", "Spring", "React", "TypeScript", "Next.js", "Python", "AWS", "Kotlin");

    private final SeniorProfileService seniorProfileService;

    @Value("${toss.payments.client-key:}")
    private String tossClientKey;

    @GetMapping("/")
    public String index(@ModelAttribute SeniorSearchCondition condition, Model model) {
        model.addAttribute("seniors", seniorProfileService.searchProfiles(condition));
        model.addAttribute("condition", condition);
        model.addAttribute("popularSkills", POPULAR_SKILLS);
        model.addAttribute("tossClientKey", tossClientKey);
        return "index";
    }
}