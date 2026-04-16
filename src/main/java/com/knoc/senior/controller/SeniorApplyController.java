package com.knoc.senior.controller;

import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import com.knoc.senior.SeniorProfileService;
import com.knoc.senior.dto.SeniorProfileRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/senior")
@RequiredArgsConstructor
public class SeniorApplyController {

    private final SeniorProfileService seniorProfileService;
    private final MemberRepository memberRepository;

    @GetMapping("/apply")
    public String apply() {
        return "senior/apply";
    }

    @GetMapping("/apply/auth")
    public String auth() {
        return "senior/apply-auth";
    }

    @GetMapping("/profile/setup")
    public String profileSetup() {
        return "senior/profile_setup";
    }

    @PostMapping("/profile/create")
    public String createProfile(@AuthenticationPrincipal UserDetails userDetails,
                                @ModelAttribute SeniorProfileRequestDto dto,
                                RedirectAttributes redirectAttributes) {
        Long memberId = getMemberId(userDetails);
        seniorProfileService.createProfile(memberId, dto);
        redirectAttributes.addFlashAttribute("successMessage", "시니어 프로필이 등록되었습니다.");
        return "redirect:/";
    }

    private Long getMemberId(UserDetails userDetails) {
        Member member = memberRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        return member.getId();
    }

    @GetMapping("/profile/update")
    public String profileUpdate(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        Long memberId = getMemberId(userDetails);
        model.addAttribute("profile", seniorProfileService.getProfile(memberId));
        return "senior/profile_update";
    }

    @PostMapping("/profile/update")
    public String updateProfile(@AuthenticationPrincipal UserDetails userDetails,
                                @ModelAttribute SeniorProfileRequestDto dto,
                                RedirectAttributes redirectAttributes) {
        Long memberId = getMemberId(userDetails);
        seniorProfileService.updateProfile(memberId, dto);
        redirectAttributes.addFlashAttribute("successMessage", "시니어 프로필이 수정되었습니다.");
        return "redirect:/senior/profile-update";
    }

}