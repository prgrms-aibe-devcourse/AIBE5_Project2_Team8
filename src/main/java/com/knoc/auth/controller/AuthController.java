package com.knoc.auth.controller;

import com.knoc.auth.dto.SignUpDto;
import com.knoc.global.exception.BusinessException;
import com.knoc.member.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Tag(name="Auth-Controller",description = "회원 인증 관련 API")
@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MemberService memberService;

    @Operation(summary = "로그인 페이지 조회", description = "로그인 폼 화면을 반환합니다.")
    @GetMapping("/login")
    public String loginForm() {
        return "auth/login";
    }

    @Operation(summary = "회원가입 페이지 조회", description = "회원가입 폼 화면을 반환합니다.")
    @GetMapping("/signup")
    public String signUpForm(Model model) {
        model.addAttribute("signupDto", new SignUpDto());
        return "auth/signup";
    }

    @Operation(summary = "회원가입 처리", description = "전달받은 정보를 바탕으로 회원가입을 처리합니다.")
    @PostMapping("/signup")
    public String processSignUp(@Valid @ModelAttribute("signupDto") SignUpDto dto, BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {

        // 1. 폼 형식 검증 에러 처리 (비밀번호 자리수, 이메일 형식 등)
        if(bindingResult.hasErrors()) {
            return "auth/signup"; // 에러가 있으면 다시 회원가입 페이지
        }
        // 이메일, 닉네임 중복 검사
        try {
            memberService.registerMember(dto);
            redirectAttributes.addFlashAttribute("successMessage", "회원가입이 완료되었습니다!");
            return "redirect:/auth/login"; // 성공 시 로그인 페이지로 이동
        } catch(BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("signupDto", dto);
            return "auth/signup"; // 에러 발생 시 에러 메시지 출력 후 화원가입 페이지 유지
        }
    }
}
