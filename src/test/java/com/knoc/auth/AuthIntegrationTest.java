package com.knoc.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knoc.member.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc  // 테스트 용 서버
@Transactional  // 테스트 후 db 자동 롤백
public class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MemberRepository memberRepository;

    //회원가입 테스트
    @Test
    @DisplayName("회원 가입 성공 시 데이터가 db에 정상적으로 저장되고 로그인 페이지로 이동한다")
    void signUpSuccessTest() throws Exception {
        mockMvc.perform(post("/auth/signup")
                .param("email", "test-user@email.com")
                .param("password", "password123!")
                .param("nickname", "테스트 유저1")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login"));

        assertThat(memberRepository.findByEmail("test-user@email.com")).isPresent();

    }
    @Test
    @DisplayName("회원가입 실패: 이미 가입된 이메일인 경우 다시 가입 폼을 보여준다")
    void signUpFailDuplicatedEmailTest() throws Exception {
        signUpSuccessTest();

        mockMvc.perform(post("/auth/signup")
                .param("email", "test-user@email.com") // 중복된 이메일
                .param("password", "password123!")
                .param("nickname", "테스트 유저2")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/signup"))
                .andExpect(model().attributeExists("errorMessage"));
    }
    @Test
    @DisplayName("회원가입 실패: 이미 존재하는 닉네임일 경우 다시 가입 폼을 보여준다")
    void signUpFailDuplicatedNickname() throws Exception {
        signUpSuccessTest();

        mockMvc.perform(post("/auth/signup")
                        .param("email", "test-user-new@email.com")
                        .param("password", "password123!")
                        .param("nickname", "테스트 유저1")  // 중복된 닉네임
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/signup"))
                .andExpect(model().attributeExists("errorMessage"));
    }
    @Test
    @DisplayName("회원가입 실패: 이메일 형식 및 비밀번호 공식 위반")
    void signUpFailValidationTest() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .param("email", "wrong-format")
                        .param("password", "password123!")
                        .param("nickname", "홍길동")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/signup"))
                .andExpect(model().attributeHasFieldErrors("signupDto", "email"));

        mockMvc.perform(post("/auth/signup")
                        .param("email", "test-user@email.com")
                        .param("password", "pass")
                        .param("nickname", "홍길동")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/signup"))
                .andExpect(model().attributeHasFieldErrors("signupDto", "password"));
    }

    // 로그인 테스트
    @Test
    @DisplayName("로그인 성공 시 쿠키에 토큰이 생성되고 메인으로 리다이렉트된다")
    void loginSuccessTest() throws Exception {
        signUpSuccessTest();

        mockMvc.perform(post("/auth/login")
                .param("username", "test-user@email.com")
                .param("password", "password123!")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(cookie().exists("accessToken"))
                .andExpect(cookie().exists("refreshToken"));
    }
    @Test
    @DisplayName("잘못된 정보 입력 시 로그인 실패")
    void loginFailureTest() throws Exception {
        signUpSuccessTest();

        mockMvc.perform(post("/auth/login")
                        .param("username", "test-user@email.com")
                        .param("password", "wrongPassword")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login?error=true"));
    }

    // 인가 테스트
    @Test
    @DisplayName("인가 성공: SENIOR 권한으로 시니어 페이지 접근 성공")
    @WithMockUser(roles = "SENIOR")
    void accessGrantedTest() throws Exception {
        mockMvc.perform(post("/senior/profile/update"))
                .andExpect(status().isOk());
    }
    @Test
    @DisplayName("인가 실패: USER 권한으로 시니어 접근 시 403 에러")
    @WithMockUser(roles = "USER")
    void accessDeniedTest() throws Exception {
        mockMvc.perform(get("/senior/profile/update"))
                .andExpect(status().isForbidden());
    }
    @Test
    @DisplayName("인가 실패: 로그인 하지 않은 사용자가 보호된 페이지 접근")
    void authorizationFailAnonymous() throws Exception {
        mockMvc.perform(post("/senior/profile/update"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/auth/login"));
    }
    @Test
    @DisplayName("인가 성공: 인증 없이 전근 가능한 페이지")
    void authorizationSuccessPermitAllTest() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());

    }

}
