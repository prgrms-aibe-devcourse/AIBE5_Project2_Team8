package com.knoc.senior.controller;

import com.knoc.auth.service.EmailVerificationService;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import com.knoc.senior.SeniorProfileService;
import com.knoc.senior.dto.SeniorProfileRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/senior")
@RequiredArgsConstructor
public class SeniorApplyController {

    private final SeniorProfileService seniorProfileService;
    private final MemberRepository memberRepository;
    private final EmailVerificationService emailVerificationService;

    @GetMapping("/apply")
    public String apply() {
        return "senior/apply";
    }

    @GetMapping("/apply/auth")
    public String auth(@AuthenticationPrincipal UserDetails userDetails) {
        // Username == Email
        Member member = memberRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다"));

        boolean alreadyVerified = emailVerificationService.isVerified(member);

        if(alreadyVerified){
            return "redirect:/senior/profile/setup";
        }

        return "senior/apply-auth";
    }

    @GetMapping("/profile/setup")
    public String profileSetup(@AuthenticationPrincipal UserDetails userDetails,
                               RedirectAttributes redirectAttributes) {
        Member member = memberRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        if (!emailVerificationService.isVerified(member)) {
            redirectAttributes.addFlashAttribute("errorMessage", "기업 이메일 인증 후 접근 가능합니다.");
            return "redirect:/senior/apply/auth";
        }

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

    @PostMapping("/verify/send")
    @ResponseBody
    public ResponseEntity<Map<String, String>> sendVerificationCode(@AuthenticationPrincipal UserDetails userDetails, @RequestParam String companyEmail){
        try {
            Member member = memberRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다"));

            emailVerificationService.sendCode(member, companyEmail);
            return ResponseEntity.ok(Map.of("message", "인증번호가 발송되었습니다"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
        catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "메일 발송에 실패했습니다"));
        }
    }

    @PostMapping("/verify/confirm")
    @ResponseBody
    public ResponseEntity<Map<String, String>> confirmVerificationCode(@AuthenticationPrincipal UserDetails userDetails, @RequestParam String inputCode){
        try {
            Member member = memberRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다"));

            emailVerificationService.verifyCode(member, inputCode);
            return ResponseEntity.ok(Map.of("message", "이메일 인증이 완료되었습니다"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

}