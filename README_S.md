<h1 align="center"> Knoc. </h1>


<p align="center">
  <strong>"완벽한 사수를 만나는 가장 가벼운 노크" </strong>
  <br />
  개발 기간 : 2026.04.15 ~ 2026.04.28 (14일)  
</p>
<h2 align="center"> 🗂️ Project Overview </h2> 
<p align="center">
  Knoc은 사수 없는 주니어 개발자와 현직 시니어 개발자를 매칭하여, 단편적인 버그 픽스가 아닌 <br/>
  <strong>실무 수준의 아키텍처 피드백과 코드 리뷰를 제공하는 플랫폼입니다. </strong> <br/>
  <br />
  개발 기간 : 2026.04.15 ~ 2026.04.28 (14일)  
</p>

<h2 align="center"> 🛠️ Features </h2>

<table align="center">
  <thead>
    <tr>
      <th>기능</th>
      <th>설명</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>시니어 탐색 & 매칭</td>
      <td>QueryDSL 동적 쿼리로 기술 스택, 경력, 가격, 필터 검색</td>
    </tr>
    <tr>
      <td>1:1 실시간 채팅</td>
      <td>WebSocket(STOMP) 기반 실시간으로 진행되는 코드 리뷰</td>
    </tr>
    <tr>
      <td>결제 시스템</td>
      <td>Toss Payments SDK, 낙관적 락 + 멱등성 키로 중복 결제를 방지합니다</td>
    </tr>
    <tr>
      <td>Github PR 연동</td>
      <td>자신의 PR의 링크를 제출하는 것만으로 변경 내역을 자동 파싱하여 코드 리뷰를 보다 원활하게 진행합니다</td>
    </tr>
    <tr>
      <td>2-Pane 워크스페이스</td>
      <td>PR 코드 뷰어 / 리뷰 리포트 + 실시간 채팅을 한 화면에 확인하실 수 있습니다</td>
    </tr>
    <tr>
      <td>구조화 리뷰 리포트</td>
      <td>현업 관점 / 엣지 케이스 / 확장성 대안 3섹션 템플릿으로 보다 구체적인 조언을 얻을 수 있습니다</td>
    </tr>
  </tbody>
</table>

<h2 align="center"> 🛠️ Feature Details </h2>
<h3 align="center"> 🔎 시니어 탐색 & 매칭 </h3>
<div align="center">
  <img src="./t3Project/readme_img/login.gif" width="600" />
  <br/>
  <blockquote>시니어를 탐색 & 매칭!</blockquote>
  <br/>
</div>
<h3 align="center"> 💬 1:1 실시간 채팅 </h3>
<div align="center">
  <img src="./t3Project/readme_img/mypage.gif"/>
  <br />
  <blockquote>1:1 채팅으로 리뷰 가능 여부를 자유롭게 사전 조율</blockquote>
  <br/>
</div>
<h3 align="center"> 💳 결제 시스템 </h3>
<div align="center">
  <img src="./t3Project/readme_img/index.gif"/>
  <br/>
  <blockquote>안전한 대금 보관, 구매 확정 후 정산</blockquote>
  <br/>
</div>
<h3 align="center"> 🔌 GitHub PR 연동 </h3>
<div align="center">
  <img src="./t3Project/readme_img/ai.gif"/>
  <br />
  <blockquote>PR 링크 제출 시 변경 사항 자동 파싱 + AI 요약</blockquote>
  <br/>
</div>
<h3 align="center"> ✍️ 2-Pane 워크 스페이스 </h3>
<div align="center">
  <img src="./t3Project/readme_img/article-1.gif"/>
  <br/>
  <blockquote>PR 코드 / 리뷰 리포트 + 실시간 채팅을 한 화면에</blockquote>
  <br/>
  <img src="./t3Project/readme_img/article-2.gif"/>
  <br/>
</div>
<h3 align="center"> 📝 구조화 리뷰 리포트 </h3>
<div align="center">
  <img src="./t3Project/readme_img/map.gif"/>
  <br/>
  <blockquote>[현업 관점] · [엣지 케이스] · [확장성 대안] 템플릿</blockquote>
  <br/>
</div>
</div>
<h2 align="center"> 🔧 Stacks </h2>

| 구분 | 기술 | 도입 배경 |
| :--- | :--- | :--- |
| **Backend** | **Java 17**, **Spring Boot 3.x** | 생산성과 안정성이 검증된 최신 스프링 생태계 활용 |
| **Security** | **Spring Security**, **JWT** | 주니어/시니어 권한 분리 및 무상태(Stateless) 인증 체계 구축 |
| **Database** | **MySQL 8.0**, **JPA**, **QueryDSL** | 객체 지향적 데이터 관리 및 복잡한 동적 쿼리(필터 검색) 최적화 |
| **Real-time** | **WebSocket (STOMP)** | 2-Pane 워크스페이스 내 실시간 소통 및 시스템 알림 구현 |
| **AI & API** | **OpenAI API**, **GitHub REST API** | 코드 자동 요약 기능 및 외부 PR 데이터 프록시 연동 |
| **DevOps** | **Docker**, **GitHub Actions** | 개발 환경의 일관성 유지 및 CI/CD 파이프라인 자동화 |
</p><br />
<h2 align="center"> 📂 Structure </h2>

~~~text
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
~~~

<h2 align="center"> 🔗 Demo & Links </h2>
<ul>
	<li>프레젠테이션 - <a href="https://docs.google.com/presentation/d/1Stx4qVFX5Pn650CVVQTg8OWFn1rIdtfx/edit?slide=id.p5#slide=id.p5https://www.miricanvas.com/v2/design2/v/7a46caab-c4fb-450b-a81a-32586ce98cc7">Knoc - PPT</li>
	<li>시연 영상 - ( 유튜브 링크 예정 있으면 추가 )</li>
</ul>

<h2 align="center"> 🫶 "Team IST-8" </h2>
<table width="100%">
  <tr>
    <td align="center"><b>정환철</b></td>
    <td align="center"><b>김세희</b></td>
    <td align="center"><b>형성빈</b></td>
    <td align="center"><b>홍가현</b></td>
  </tr>
  <tr>
    <td align="center"><img src="https://avatars.githubusercontent.com/u/233332353?v=4" width="100"/></td>
    <td align="center"><img src="https://avatars.githubusercontent.com/u/80444956?v=4" width="100"/></td>
    <td align="center"><img src="https://avatars.githubusercontent.com/u/74960635?v=4" width="100"/></td>
    <td align="center"><img src="https://avatars.githubusercontent.com/u/71168366?v=4" width="100"/></td>
  </tr>
  <tr>
    <td align="center"><a href="https://github.com/jhwan1205-sys">@jhwan1205-sys</a></td>
    <td align="center"><a href="https://github.com/kimsehee-8135">@kimsehee-8135</a></td>
    <td align="center"><a href="https://github.com/hsb4225">@hsb4225</a></td>
    <td align="center"><a href="https://github.com/devken65">@devken65</a></td>
  </tr>
  <tr>
    <td align="center">🧠 <br/> 팀장 <br/> QueryDSL 동적 검색 <br/> GitHub API 연동 <br/> 백엔드 로직 및 <br/> UI/UX 화면 구현</td>
    <td align="center">💳 <br/> 팀원 <br/> 결제 로직 담당 <br/> 토스페이먼츠 결제 API <br/> 백엔드 로직 및 <br/> UI/UX 화면 구현</td>
    <td align="center">🔐 <br/> 팀원 <br/> 로그인/회원가입 JWT 인증 <br/> 백엔드 로직 및 <br/> UI/UX 화면 구현</td>
    <td align="center">💬 <br/> 팀원 <br/> WebSocket 1:1 채팅방 <br/> 백엔드 로직 및 <br/> UI/UX 화면 구현</td>
  </tr>
</table>
