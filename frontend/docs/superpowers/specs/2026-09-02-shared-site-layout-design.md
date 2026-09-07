# 공통 Header/Footer 컴포넌트 설계

## 목표

랜딩 페이지에 결합된 Header와 Footer를 다른 프론트엔드 페이지에서도 데이터만 전달해 사용할 수 있는 공통 레이아웃 컴포넌트로 분리한다. 현재 랜딩 페이지의 화면, 반응형 동작, 접근성 및 테스트 동작은 유지한다.

## 현재 문제

- `Header`와 `Footer`가 `src/pages/landing/components/`에 있어 페이지 전용 컴포넌트처럼 보인다.
- Header가 랜딩 메뉴, `ShopContext`, `WalletButton`에 직접 의존한다.
- Footer가 랜딩 푸터 데이터와 표시 문구를 직접 가져온다.
- Header/Footer 스타일이 페이지 전체 스타일 파일인 `landing.css`에 섞여 있어 컴포넌트만 가져오면 스타일 소유권이 불명확하다.

## 디렉토리 구조

```text
src/
├─ shared/
│  └─ layout/
│     ├─ SiteHeader.tsx
│     ├─ SiteFooter.tsx
│     ├─ site-layout.css
│     └─ site-layout.test.tsx
└─ pages/
   └─ landing/
      ├─ LandingPage.tsx
      ├─ content.ts
      └─ landing.css
```

기존 `pages/landing/components/Header.tsx`와 `Footer.tsx`는 마이그레이션 후 삭제한다.

## SiteHeader 계약

`SiteHeader`는 다음 값을 props로 받는다.

- `navigation`: 표시할 메뉴의 `label`과 `href`
- `cartCount`: 장바구니 개수
- `cartHref`: 장바구니 이동 경로
- `walletContent`: 지갑 팝오버에 렌더링할 React 노드
- `logoSrc`, `homeHref`, `homeLabel`: 로고와 홈 링크 정보

컴포넌트 내부 책임은 다음과 같다.

- 데스크톱 및 모바일 메뉴 렌더링
- 모바일 메뉴 열기/닫기와 링크 선택 후 트리거 포커스 복원
- 지갑 팝오버 열기/닫기와 `aria-expanded`, `aria-controls` 제공
- 검색, 장바구니, 메뉴 버튼의 접근 가능한 이름 유지

`SiteHeader`는 `ShopContext`, `WalletButton`, 랜딩 콘텐츠를 import하지 않는다. 각 페이지가 필요한 상태와 콘텐츠를 props로 조합한다.

## SiteFooter 계약

`SiteFooter`는 다음 값을 props로 받는다.

- 로고와 홈 링크 정보
- 소개 문구
- 소셜 링크 목록과 아이콘 React 노드
- 푸터 메뉴 그룹 목록
- 뉴스레터 제목, 설명, 입력 및 피드백 문구
- 저작권 문구와 법적 링크 목록
- 국가·통화 선택 버튼 문구

컴포넌트 내부 책임은 다음과 같다.

- 전달받은 링크 그룹과 소셜 링크 렌더링
- 이메일 필수값 및 형식 검증
- 제출 성공·오류 상태와 `alert`/`status` 접근성 제공
- 선택적으로 전달된 구독 콜백 호출

표시 문구와 이동 경로는 props에서 오며, `SiteFooter`는 랜딩 콘텐츠를 import하지 않는다.

## 랜딩 페이지 조합

`LandingPage`는 현재 `ShopContext`에서 장바구니 수량을 읽고 `WalletButton`을 생성해 `SiteHeader`에 전달한다. 랜딩 전용 메뉴·푸터 데이터는 `content.ts`에서 가져와 공통 컴포넌트 props로 전달한다.

상품, 브랜드, 신뢰 영역과 Hero는 이번 작업에서 이동하거나 재설계하지 않는다.

## 스타일 소유권

- Header/Footer 전용 선택자와 관련 반응형 규칙을 `shared/layout/site-layout.css`로 이동한다.
- 공통 컴포넌트가 자신의 CSS를 직접 import한다.
- 랜딩 섹션, 카드, Hero, 페이지 배경 관련 규칙은 `landing.css`에 유지한다.
- 공통 레이아웃 색상은 전역 브랜드 CSS 변수와 안전한 fallback 값을 사용한다.
- 기존 데스크톱·모바일 픽셀 배치, 44px 터치 영역, 포커스 링과 대비를 유지한다.

## 테스트 전략

테스트를 먼저 추가해 기존 컴포넌트에서 실패하는 것을 확인한 후 마이그레이션한다.

- 공통 Header가 props로 받은 메뉴·장바구니·지갑 콘텐츠를 렌더링하는지 검증
- 모바일 메뉴 링크 선택 후 닫힘과 포커스 복원 검증
- 지갑 팝오버 상태와 전달 콘텐츠 검증
- 공통 Footer가 props로 받은 링크와 문구를 렌더링하는지 검증
- 뉴스레터 필수값·잘못된 형식·성공 상태 및 구독 콜백 검증
- 기존 랜딩 페이지 통합 테스트로 최종 조합과 화면 동작 회귀 검증
- 전체 테스트, ESLint, TypeScript/Vite 프로덕션 빌드 수행

## 오류 처리

- 장바구니 수량은 음수가 전달돼도 화면에는 `0` 이상으로 표시한다.
- 뉴스레터 구독 콜백이 실패하면 전달받은 실패 문구를 `role="alert"`로 표시한다.
- 필수 배열은 빈 배열을 허용하며, 해당 메뉴 영역은 빈 상태로 안전하게 렌더링한다.

## 비대상

- 상품·브랜드 카드 추가 분리
- 인증, 장바구니 또는 지갑 상태 구조 변경
- 새로운 페이지나 라우트 추가
- 백엔드 API 또는 영속 뉴스레터 구독 구현
- 랜딩 페이지 시각 디자인 변경

## 완료 조건

- Header/Footer가 `src/shared/layout/`에서 export된다.
- 두 컴포넌트가 랜딩 페이지 데이터 및 기능 구현에 직접 의존하지 않는다.
- 랜딩 페이지가 props로 상태와 콘텐츠를 주입한다.
- 기존 디자인과 핵심 상호작용이 유지된다.
- 관련 테스트, 전체 테스트, 린트, 빌드 및 디자인 회귀 확인이 통과한다.
