package com.knoc.global.controller;

import com.knoc.senior.SeniorProfileService;
import com.knoc.senior.dto.SeniorSearchCondition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@Tag(name="Index-Controller",description = "메인페이지 및 시니어 조회 관련 API")
@Controller
@RequiredArgsConstructor
public class IndexController {

    private static final List<String> POPULAR_SKILLS =
            List.of("Java", "Spring", "React", "TypeScript", "Next.js", "Python", "AWS", "Kotlin");

    private final SeniorProfileService seniorProfileService;

    @Value("${toss.payments.client-key:}")
    private String tossClientKey;

    @Operation(summary = "메인 페이지 조회",description = "메인 페이지에 접속하여 검색 조건에 따른 시니어 목록 조회합니다.")
    @GetMapping("/")
    public String index(@ModelAttribute SeniorSearchCondition condition, Model model) {
        model.addAttribute("seniors", seniorProfileService.searchProfiles(condition));
        model.addAttribute("condition", condition);
        model.addAttribute("popularSkills", POPULAR_SKILLS);
        model.addAttribute("tossClientKey", tossClientKey);
        return "index";
    }
}