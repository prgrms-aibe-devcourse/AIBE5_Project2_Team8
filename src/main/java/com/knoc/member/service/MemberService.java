package com.knoc.member.service;

import com.knoc.auth.dto.SignUpDto;
import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import com.knoc.global.service.FileStorageService;
import com.knoc.member.Member;
import com.knoc.member.MemberRepository;
import com.knoc.member.MemberRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;

    @Transactional
    public void updateProfile(String email, String nickname, MultipartFile profileImage) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (!member.getNickname().equals(nickname) && memberRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }
        String imageUrl = (profileImage != null && !profileImage.isEmpty())
                ? fileStorageService.store(profileImage)
                : null;
        member.updateProfile(nickname, imageUrl);
    }

    @Transactional
    public void registerMember(SignUpDto dto) {
        if(memberRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("이미 사용중인 이메일입니다.");

        }
        if(memberRepository.existsByNickname(dto.getNickname())) {
            throw new IllegalArgumentException("이미 사용중인 닉네임입니다.");
        }

        // 엔티티 생성 및 암호화된 비밀번호 저장
        Member member = Member.builder()
                .nickname(dto.getNickname())
                .email(dto.getEmail())
                .password(passwordEncoder.encode(dto.getPassword()))
                .role(MemberRole.USER) // 기본 권한(주니어)
                .build();

        memberRepository.save(member);
    }
}
