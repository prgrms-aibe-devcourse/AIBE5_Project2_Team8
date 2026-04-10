# 🚪 Knoc (노크)
> **"완벽한 사수를 만나는 가장 가벼운 노크"**
> 주니어와 시니어를 잇는 가장 가벼운 시작, 가장 깊은 코드 리뷰 플랫폼

<br>

## 💡 서비스 개요 (Service Overview)
**Knoc**은 주니어 개발자들이 겪는 '좋은 사수의 부재'와 '코드 리뷰의 높은 심리적 장벽'을 해결하기 위해 기획되었습니다. 
복잡한 양식과 절차 대신, 평소 사용하는 메신저처럼 가볍게 '노크'하는 채팅 UI를 통해 현업 시니어의 인사이트를 전수받습니다.

* **가벼운 채팅 기반 의뢰:** 상담부터 결제, 리뷰 요청까지 모든 과정이 익숙한 채팅 UI 내에서 원스톱으로 이루어집니다.
* **2-Pane 통합 워크스페이스:** GitHub PR 코드와 시니어의 리뷰 리포트를 한 화면에서 보며 실시간으로 질의응답합니다.
* **똑똑한 AI 비서:** 방대한 코드 변경 내역(Diff)과 대화 내용을 AI가 핵심 3줄로 요약하여 빠른 상황 파악을 돕습니다.

<br>

## 🛠 기술 스택 (Tech Stack)

| 구분 | 기술 | 도입 배경 |
| :--- | :--- | :--- |
| **Backend** | **Java 17**, **Spring Boot 3.x** | 생산성과 안정성이 검증된 최신 스프링 생태계 활용 |
| **Security** | **Spring Security**, **JWT** | 주니어/시니어 권한 분리 및 무상태(Stateless) 인증 체계 구축 |
| **Database** | **MySQL 8.0**, **JPA**, **QueryDSL** | 객체 지향적 데이터 관리 및 복잡한 동적 쿼리(필터 검색) 최적화 |
| **Real-time** | **WebSocket (STOMP)** | 2-Pane 워크스페이스 내 실시간 소통 및 시스템 알림 구현 |
| **AI & API** | **OpenAI API**, **GitHub REST API** | 코드 자동 요약 기능 및 외부 PR 데이터 프록시 연동 |
| **DevOps** | **Docker**, **GitHub Actions** | 개발 환경의 일관성 유지 및 CI/CD 파이프라인 자동화 |

<br>

## 🏗 핵심 기술적 의사결정 (Architecture Decisions)

### 1. RDBMS 기반의 동시성 제어 전략 (No Redis)
에스크로 결제 및 정산 과정에서 발생할 수 있는 데이터 정합성 문제를 해결하기 위해 추가적인 인프라 도입 대신 **MySQL의 락(Lock) 메커니즘**을 적극 활용했습니다.
* **낙관적 락(@Version):** 평점 업데이트 등 충돌 빈도가 낮은 정산 도메인에 적용하여 성능을 확보했습니다.
* **비관적 락(Pessimistic Write):** 중복 결제 방지 및 상태 전이 등 정합성이 최우선인 로직에 적용하여 데이터 무결성을 보장했습니다.

### 2. 이벤트 기반 아키텍처(EDA)를 통한 도메인 결합도 해소
결제 완료, 리뷰 등록 등 핵심 비즈니스 로직과 채팅 시스템 메시지 발송 로직 간의 **강한 결합(Tight Coupling)**을 끊기 위해 **Spring ApplicationEventPublisher**를 도입했습니다. 
* 이를 통해 도메인 로직의 성공 여부와 관계없이 시스템 알림이 독립적으로 동작하며, 시스템의 확장성을 높였습니다.

### 3. @Async를 활용한 비동기 AI 처리 및 성능 최적화
LLM(OpenAI)의 응답 지연이 사용자의 메인 트랜잭션(결제, 구매 확정 등)을 방해하지 않도록 **비동기(@Async) 처리**를 적용했습니다. 
* AI 요약 생성 시 별도의 백그라운드 스레드를 할당하고, 완료 시 WebSocket을 통해 결과를 실시간 브로드캐스팅하여 사용자 경험(UX)을 개선했습니다.

<br>

## 🤝 팀 협업 컨벤션 (Team Collaboration)

### 🌿 브랜치 전략 (Branch Strategy)
**GitHub Flow**를 기반으로 운영하며, 모든 작업은 이슈 단위로 브랜치를 생성합니다.
* `main` : 최종 배포 브랜치
* `develop` : 개발 통합 브랜치 (기본 base 브랜치)
* `feature/#이슈번호-기능명` : 신규 기능 개발
* `fix/#이슈번호-버그명` : 버그 수정

### 📝 커밋 메시지 규칙 (Commit Message)
포맷: `[태그] 작업 내용 요약 (#이슈번호)`
* `[Feat]` : 새로운 기능 추가
* `[Fix]` : 버그 수정
* `[Refactor]` : 코드 리팩토링
* `[Design]` : CSS, Thymeleaf 등 UI 변경
* `[Docs]` : 문서 수정 (README 등)

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

<br>
