package com.knoc.senior.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import com.knoc.auth.service.EmailVerificationService;
import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
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

@Tag(name="Senior-Controller",description = "시니어 지원 및 신청 및 프로필 관리 관련 API")
@Controller
@RequestMapping("/senior")
@RequiredArgsConstructor
public class SeniorApplyController {

    private final SeniorProfileService seniorProfileService;
    private final MemberRepository memberRepository;
    private final EmailVerificationService emailVerificationService;

    @Operation(summary = "시니어 지원 안내 페이지", description = "시니어 지원을 위한 안내 페이지를 보여줍니다.")
    @GetMapping("/apply")
    @PreAuthorize("hasRole('USER')")
    public String apply() {
        return "senior/apply";
    }

    @Operation(summary = "시니어 이메일 인증 페이지", description = "시니어 인증을 위한 이메일 인증 페이지를 보여줍니다.")
    @GetMapping("/apply/auth")
    public String auth(@AuthenticationPrincipal UserDetails userDetails) {
        // Username == Email
        Member member = memberRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        boolean alreadyVerified = emailVerificationService.isVerified(member);

        if(alreadyVerified){
            return "redirect:/senior/profile/setup";
        }

        return "senior/apply-auth";
    }

    @Operation(summary = "초기 프로필 설정 페이지", description = "이메일 인증 후 시니어 프로필 생성을 위한 페이지를 보여줍니다.")
    @GetMapping("/profile/setup")
    public String profileSetup(@AuthenticationPrincipal UserDetails userDetails,
                               RedirectAttributes redirectAttributes) {
        Member member = memberRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (!emailVerificationService.isVerified(member)) {
            redirectAttributes.addFlashAttribute("errorMessage", "기업 이메일 인증 후 접근 가능합니다.");
            return "redirect:/senior/apply/auth";
        }

        return "senior/profile_setup";
    }

    @Operation(summary = "시니어 프로필 생성", description = "작성한 정보를 바탕으로 시니어 프로필을 생성합니다.")
    @PostMapping("/profile/create")
    @PreAuthorize("hasRole('SENIOR')")
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
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        return member.getId();
    }

    @Operation(summary = "시니어 프로필 수정 화면 조회", description = "현재 본인의 시니어 프로필 수정 화면을 보여줍니다.")
    @GetMapping("/profile/update")
    @PreAuthorize("hasRole('SENIOR')")
    public String profileUpdate(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        Long memberId = getMemberId(userDetails);
        model.addAttribute("profile", seniorProfileService.getProfile(memberId));
        return "senior/profile_update";
    }

    @Operation(summary = "시니어 프로필 수정", description = "수정된 프로필 정보를 저장합니다.")
    @PostMapping("/profile/update")
    @PreAuthorize("hasRole('SENIOR')")
    public String updateProfile(@AuthenticationPrincipal UserDetails userDetails,
                                @ModelAttribute SeniorProfileRequestDto dto,
                                RedirectAttributes redirectAttributes) {
        Long memberId = getMemberId(userDetails);
        seniorProfileService.updateProfile(memberId, dto);
        redirectAttributes.addFlashAttribute("successMessage", "시니어 프로필이 수정되었습니다.");
        return "redirect:/senior/profile-update";
    }

    @Operation(summary = "이메일 인증 번호 발송",description = "기업 이메일로 인증번호를 발송합니다.")
    @PostMapping("/verify/send")
    @ResponseBody
    public ResponseEntity<Map<String, String>> sendVerificationCode(@AuthenticationPrincipal UserDetails userDetails, @RequestParam String companyEmail){
        Member member = memberRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        emailVerificationService.sendCode(member, companyEmail);
        return ResponseEntity.ok(Map.of("message", "인증번호가 발송되었습니다"));
    }

    @Operation(summary = "시니어 프로필 상세 조회",description = "특정 시니어의 프로필 상세 정보를 조회합니다.")
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("senior", seniorProfileService.getDetailById(id));
        return "senior/detail";
    }

    @Operation(summary = "인증번호 확인",description = "입력한 인증번호가 일치하는지 확인합니다.")
    @PostMapping("/verify/confirm")
    @ResponseBody
    public ResponseEntity<Map<String, String>> confirmVerificationCode(@AuthenticationPrincipal UserDetails userDetails, @RequestParam String inputCode){
        Member member = memberRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        emailVerificationService.verifyCode(member, inputCode);

        return ResponseEntity.ok(Map.of("message", "이메일 인증이 완료되었습니다"));
    }
}