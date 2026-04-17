package com.knoc.member;

import com.knoc.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "member")
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role;

    @Column(name = "profile_image_url", length = 512)
    private String profileImageUrl;

    @Builder
    public Member(String email, String password, String nickname, MemberRole role, String profileImageUrl) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.role = role != null ? role : MemberRole.USER;
        this.profileImageUrl = profileImageUrl;
    }

    public void promoteToSenior(){
        this.role = MemberRole.SENIOR;
    }
}