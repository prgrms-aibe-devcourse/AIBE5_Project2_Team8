# 🚪 Knoc (노크)
> **"완벽한 사수를 만나는 가장 가벼운 노크"**
> 주니어와 시니어를 잇는 가장 가벼운 시작, 가장 깊은 코드 리뷰 플랫폼

---

## 📌 프로젝트 소개

Knoc은 사수 없는 주니어 개발자와 현직 시니어 개발자를 매칭하여, 단편적인 버그 픽스가 아닌 **실무 수준의 아키텍처 피드백과 코드 리뷰**를 제공하는 플랫폼입니다.

### 핵심 기능

| 기능 | 설명 |
|------|------|
| 💬 채팅 기반 매칭 | 1:1 채팅으로 리뷰 가능 여부를 자유롭게 사전 조율 |
| 💳 에스크로 결제 | 안전한 대금 보관, 구매 확정 후 정산 |
| 🔗 GitHub PR 연동 | PR 링크 제출 시 변경 사항 자동 파싱 + AI 요약 |
| 🖥️ 2-Pane 워크스페이스 | PR 코드 / 리뷰 리포트 + 실시간 채팅을 한 화면에 |
| 📋 구조화된 리뷰 리포트 | [현업 관점] · [엣지 케이스] · [확장성 대안] 템플릿 |
| 🤖 AI 요약 | PR 변경사항 요약 + 멘토링 핵심 3줄 요약 |

### 서비스 플로우

```
시니어 탐색 → 1:1 채팅 문의 → 에스크로 결제 → 리뷰 요청서 제출
    → 2-Pane 워크스페이스 (코드 리뷰 + 실시간 채팅) → 구매 확정 & 정산
```

---

## 🛠 기술 스택 (Tech Stack)

| 구분 | 기술 | 도입 배경 |
| :--- | :--- | :--- |
| **Backend** | **Java 17**, **Spring Boot 3.x** | 생산성과 안정성이 검증된 최신 스프링 생태계 활용 |
| **Security** | **Spring Security**, **JWT** | 주니어/시니어 권한 분리 및 무상태(Stateless) 인증 체계 구축 |
| **Database** | **MySQL 8.0**, **JPA**, **QueryDSL** | 객체 지향적 데이터 관리 및 복잡한 동적 쿼리(필터 검색) 최적화 |
| **Real-time** | **WebSocket (STOMP)** | 2-Pane 워크스페이스 내 실시간 소통 및 시스템 알림 구현 |
| **AI & API** | **OpenAI API**, **GitHub REST API** | 코드 자동 요약 기능 및 외부 PR 데이터 프록시 연동 |
| **DevOps** | **Docker**, **GitHub Actions** | 개발 환경의 일관성 유지 및 CI/CD 파이프라인 자동화 |

---

## 🚀 로컬 환경 세팅

### 사전 요구사항

- Java 17+
- Docker & Docker Compose
- IntelliJ IDEA (권장)
- Git

### 1. 저장소 클론

```bash
git clone https://github.com/prgrms-aibe-devcourse/AIBE5_Project2_Team8.git
cd AIBE5_Project2_Team8
git checkout develop
```

### 2. 환경변수 설정

```bash
cp .env.example .env
```

`.env` 파일을 열어 아래 값들을 채워주세요:

```
DB_URL=jdbc:mysql://localhost:3306/knoc
DB_USERNAME=root
DB_PASSWORD=yourpassword
JWT_SECRET=your-256-bit-secret-key-here
```

### 3. 애플리케이션 실행

브라우저에서 `http://localhost:8080` 접속

### 4. (선택) IntelliJ 설정

- **Lombok 플러그인** 설치 확인
- Settings → Build → Compiler → Annotation Processors → **Enable annotation processing** 체크
- Gradle JVM → **Java 17** 선택

---

## 📂 프로젝트 구조

```
src/main/java/com/knoc/
├── KnocApplication.java
├── global/                  # 공통 인프라 (config, entity, exception, util)
├── auth/                    # 인증/인가 (JWT, 이메일 인증)
├── member/                  # 회원
├── senior/                  # 시니어 프로필, 검색 (QueryDSL)
├── chat/                    # 실시간 채팅 (WebSocket STOMP)
├── event/                   # 도메인 이벤트, 리스너
├── order/                   # 주문, 에스크로 결제
├── review/                  # 리뷰 요청/리포트, GitHub API, AI 연동
├── settlement/              # 정산, 후기
└── dashboard/               # 대시보드

src/main/resources/
├── templates/
│   ├── layout/fragments/    # 공통 레이아웃 (header, footer)
│   ├── auth/                # 로그인, 회원가입
│   ├── senior/              # 시니어 목록, 상세
│   ├── chat/                # 채팅방
│   ├── workspace/           # 2-Pane 워크스페이스
│   ├── review/              # 리뷰 요청서, 리포트
│   ├── dashboard/           # 주니어/시니어 대시보드
│   └── error/               # 에러 페이지
└── static/
    ├── css/
    ├── js/
    └── images/
```

---

## 🤝 팀 협업 컨벤션 (Team Collaboration)

### 🌿 브랜치 전략 (Branch Strategy)
**GitHub Flow**를 기반으로 운영하며, 모든 작업은 이슈 단위로 브랜치를 생성합니다.
* `main` : 최종 배포 브랜치
* `develop` : 개발 통합 브랜치 (기본 base 브랜치)
* `feature/#이슈번호-기능명` : 신규 기능 개발
* `fix/#이슈번호-버그명` : 버그 수정

### 📝 커밋 메시지 규칙 (Commit Message)
포맷: `태그: 작업 내용 요약 (#이슈번호)`
* `feat` : 새로운 기능 추가
* `fix` : 버그 수정
* `refactor` : 코드 리팩토링
* `design` : CSS, Thymeleaf 등 UI 변경
* `docs` : 문서 수정 (README 등)

### 🎫 이슈 및 PR 관리 (Issue & PR Management)
모든 작업은 이슈 생성 후 진행하며, PR을 통해 최소 1명 이상의 코드 리뷰를 거친 뒤 `develop`에 머지합니다.

### Issue Templates

GitHub에서 "New Issue" 클릭 시 템플릿 선택 화면이 표시됩니다.

| 템플릿      | 용도                              | 라벨          |
| ----------- | --------------------------------- | ------------- |
| **Epic**    | 대규모 기능 단위 (여러 이슈 포함) | `epic`        |
| **Feature** | 단일 기능 구현                    | `enhancement` |
| **Bug**     | 버그 리포트                       | `bug`         |

#### Epic 템플릿

```markdown
## 🎯 목표

- <유저 관점에서 달성해야 할 결과>

## 📌 To do (각 항목 = 서브 이슈 1개)

> 각 To do 항목은 별도 PR로 분리하여 구현한다.

- [ ] [Feat] <작업 1>
- [ ] [Feat] <작업 2>
```

#### Feature / Bug 템플릿

```markdown
## 📝 무엇을 하나요?

- 할 일을 간단히 설명해주세요

## 📌 To do

- [ ] 할 작업들 리스트업
```

> 템플릿 파일: [.github/ISSUE_TEMPLATE/](.github/ISSUE_TEMPLATE/)

### PR Template

```markdown
## 🔎 What

- 한 작업을 간단히 설명해주세요

## 🔗 Issue

- Closes: #이슈번호

## ✅ 체크리스트

- [ ] 브랜치 base가 적절한가요?
- [ ] 제목이 이슈 제목과 동일한가요?
- [ ] 최소 1명의 리뷰를 받았나요?
```

> 템플릿 파일: [.github/PULL_REQUEST_TEMPLATE.md](.github/PULL_REQUEST_TEMPLATE.md)


---

## ⚠️ 주의사항

- `.env` 파일은 절대 커밋하지 마세요 (`.gitignore`에 포함됨)
- `main` 브랜치에 직접 push하지 마세요
