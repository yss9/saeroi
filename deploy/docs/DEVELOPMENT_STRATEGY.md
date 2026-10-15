# 초기 개발 전략 (Development Strategy)

> 코딩을 시작하기 전에 팀 전체가 합의해야 할 것들과, 초반 2주의 개발 순서를 정리한 문서입니다.
> 핵심 원칙: **기능 수를 늘리는 것이 아니라, 기반을 빨리 검증한다.**

---

## 목차

1. [팀 역할 분담](#1-팀-역할-분담)
2. [코딩 전 전체 합의 사항](#2-코딩-전-전체-합의-사항)
3. [ERD / API / Contract Interface 설계](#3-erd--api--contract-interface-설계)
4. [초기 작업 분배](#4-초기-작업-분배)
5. [첫 번째 E2E 기능: Material 등록 (Vertical Slice)](#5-첫-번째-e2e-기능-material-등록-vertical-slice)
6. [병렬 개발 단계](#6-병렬-개발-단계)
7. [기능 개발 순서 (의존성 기준)](#7-기능-개발-순서-의존성-기준)
8. [Mock 전략](#8-mock-전략)
9. [초반 2주 일정](#9-초반-2주-일정)
10. [처음부터 하지 말아야 할 것](#10-처음부터-하지-말아야-할-것)
11. [실행 체크리스트](#11-실행-체크리스트)

---

## 1. 팀 역할 분담

| 인원 | 전문 역할 | 메인 기능 |
| --- | --- | --- |
| 1번 | Blockchain | Material / Token |
| 2번 | Frontend Lead / Design | Traceability / QR / Verification |
| 3번 | Frontend + Backend | Marketplace / Trade |
| 4번 | Backend | Inventory / Processing |
| 5번 | Backend | Production / Product |
| 6번 | Infra | User / Wallet / 공통 기능 |

---

## 2. 코딩 전 전체 합의 사항

코딩 전에 **반나절~하루**를 투자해 아래 내용을 먼저 고정한다.
**이게 정해지지 않은 상태에서 Entity나 Contract부터 만들면 거의 무조건 다시 뜯게 된다.**

### 2-1. 핵심 사용자 흐름 확정

```
Supplier 소재 등록
→ Material 생성
→ Token 발행
→ Marketplace 판매
→ Brand 구매
→ 거래 완료
→ Brand Inventory
→ Processor 가공
→ 새로운 Material 생성
→ Production
→ Product 생성
→ QR Journey 조회
```

### 2-2. 핵심 정책 확정

- [ ] Material ID 정책
- [ ] Material 가공 시 Parent/Child 관계
- [ ] 수량 단위 (kg / meter)
- [ ] Token ID와 Material ID 관계
- [ ] 가공 시 기존 Token 처리 방식
- [ ] Product 생산 시 Token 처리 방식
- [ ] Wallet 서명이 필요한 시점
- [ ] On-chain / Off-chain 데이터 구분
- [ ] 거래(Trade) 상태 정의
- [ ] Processing 상태 정의
- [ ] Production 상태 정의

---

## 3. ERD / API / Contract Interface 설계

완성된 상세 명세까지는 필요 없고, **핵심 Entity와 도메인 관계**만 우선 확정한다.

### 3-1. 핵심 Entity

| 도메인 | Entity |
| --- | --- |
| 사용자 | User, Wallet |
| 소재 | Material, MaterialEvidence |
| 거래 | MaterialListing, Trade |
| 재고 | Inventory |
| 가공 | Processing |
| 생산 | ProductionBatch, Product |
| 공통 | BlockchainTransaction, Verification |

### 3-2. 도메인 관계 (데이터 흐름)

```
User
 ↓
Material
 ↓
Trade
 ↓
Inventory
 ↓
Processing
 ↓
Processed Material
 ↓
Production
 ↓
Product
 ↓
Journey
```

이 그림은 **6명 전원이 함께 봐야 한다.**
4번(Processing)이 만든 Material을 5번(Production)이 사용하고, 그 결과를 2번(Traceability)이 읽기 때문이다.

---

## 4. 초기 작업 분배

초반에는 **1 · 2 · 4 · 6번이 공통 기반**을 만들고, 3 · 5번은 자기 도메인 설계를 진행한다.

### 1번 — Blockchain: PoC부터

처음부터 전체 Contract를 만들지 말고 **작은 PoC 하나**부터 시작한다.

1. 사용할 Blockchain Network 확정
2. Solidity 개발환경 구성
3. Hardhat 구성
4. MetaMask 연결 확인
5. 아주 작은 Test Contract 배포
6. React에서 Contract 호출 성공
7. Spring에서 Blockchain 조회 성공

이후 Material Token 설계. **첫 성공 목표는 딱 여기까지:**

```
Material 10kg 등록 → Token 발행 → Wallet Balance 조회 성공
```

> ⚠️ 처음부터 Escrow + Processing + Production Contract까지 만들지 않는다.

### 2번 — Frontend Lead: 프론트 뼈대

기능 화면이 아니라 **공통 기반**을 먼저 만든다.

- React + TypeScript 프로젝트 생성
- 라우팅 / 폴더 구조 / 공통 Layout
- 공통 컴포넌트: `Button`, `Input`, `Modal`, `Card`, `Badge`, `FileUpload`, `Loading`, `Error`
- API 호출 구조
- Figma/디자인이 있으면 공통 디자인 시스템 정리

이렇게 해 두면 나머지 팀원은 `<Button />`, `<Modal />`, `<StatusBadge />`를 가져다 쓰며 자기 화면을 개발할 수 있다.

### 4번 — Backend: Spring 공통 구조

**모든 Backend 코드를 만드는 게 아니라 틀만 만든다.**

- Spring Boot 프로젝트 생성
- package 구조 / DB 연결
- BaseEntity / 공통 Exception / 공통 Response
- Security 기본 구조 / JPA 구조 / 환경변수

도메인 package는 아래까지만 우선 생성:

```
user / material / trade / inventory / processing
production / product / traceability / blockchain / common
```

### 6번 — Infra: 배포 파이프라인 최소 구성

운영환경을 처음부터 완성하려 하지 말고, 아래를 먼저:

- EC2 접속 / Docker 설치 / Docker Compose
- Nginx / Jenkins / Git Credentials / 환경변수 관리

**첫 목표:**

> 빈 프로젝트라도 Merge하면 Jenkins를 통해 EC2에 올라가는 상태
> (Hello Spring + Hello React를 실제 EC2에 배포)

### 3번 — Marketplace / Trade: 설계 선행

공통 기반을 기다리는 동안 설계를 진행한다.

- Marketplace 화면 흐름
- Trade 상태 정의
- API 요청/응답 DTO
- Entity 설계
- Escrow Flow 설계

Trade 상태 예시 (먼저 확정):

```
AVAILABLE → RESERVED → PAYMENT_PENDING → PAID
→ SHIPPED → RECEIVED → COMPLETED
```

공통 Spring 구조가 나오면 바로 구현 시작.

### 5번 — Production / Product: 설계 선행

- ProductionBatch 구조 / Product 구조
- 어떤 Material을 몇 kg 사용하는지
- 남은 수량 계산 방식
- Product와 Material 연결 방식

수량 규칙 예시 (먼저 확정):

| 항목 | 값 |
| --- | --- |
| 사용 Material | MAT-004 |
| Input | 18.6kg |
| Product Used | 15kg |
| Remaining | 3.6kg |

---

## 5. 첫 번째 E2E 기능: Material 등록 (Vertical Slice)

**가장 중요한 단계.** 공통 기반이 만들어지면 6명이 바로 자기 도메인으로 흩어지지 말고,
**Material 등록 하나를 끝까지 연결**한다.

```
React    : Material 등록 Form
   ↓
Spring   : POST /materials
   ↓
MySQL    : Material 저장
   ↓
Blockchain : Material Token 발행
   ↓
Spring   : Tx Hash 저장
   ↓
React    : 등록 완료 표시
```

여기까지 실제로 돌아가면 핵심 연결이 전부 한 번씩 검증된다:

- React ↔ Spring
- Spring ↔ MySQL
- React ↔ MetaMask
- Spring ↔ Blockchain
- Jenkins ↔ EC2

> 💡 이 Vertical Slice가 성공하기 전에는 "프로젝트 개발이 본격적으로 시작됐다"고 생각하지 않는다.
> `Material 등록 → DB 저장 → Wallet/Blockchain 처리 → Tx 상태 확인`이 한 번 끝까지 돌아가면,
> 이후의 Trade · Processing · Production은 결국 같은 패턴의 확장이다.

---

## 6. 병렬 개발 단계

Vertical Slice 성공 후 본격적인 병렬 개발로 전환한다.

| 담당 | 기능 |
| --- | --- |
| 1번 | Material / Token |
| 2번 | Traceability / QR / 공통 FE |
| 3번 | Marketplace / Trade |
| 4번 | Inventory / Processing |
| 5번 | Production / Product |
| 6번 | User / Wallet + Infra 유지 |

데이터 흐름 순서상 개발 우선순위:

```
User → Material → Marketplace/Trade → Inventory
→ Processing → Production → Product → Traceability
```

---

## 7. 기능 개발 순서 (의존성 기준)

모든 기능이 동시에 완성될 수는 없으므로, 의존성에 따라 단계를 나눈다.

| 차수 | 기능 |
| --- | --- |
| 1차 | User, Material, Wallet |
| 2차 | Marketplace, Trade, Inventory |
| 3차 | Processing |
| 4차 | Production, Product |
| 5차 | Traceability, QR, Verification |

> Traceability 담당자가 마지막까지 기다리는 것은 아니다. → [Mock 전략](#8-mock-전략) 참고

---

## 8. Mock 전략

### 8-1. Traceability — Mock 데이터로 UI 선행 개발

초반에는 Mock 데이터로 Timeline / Journey UI를 먼저 만들고, 나중에 실제 API를 연결한다.

```json
[
  { "type": "SOURCE",     "company": "Supplier A" },
  { "type": "PROCESS",    "company": "Processor B" },
  { "type": "PRODUCTION", "company": "Brand C" }
]
```

### 8-2. Blockchain — Mock Interface로 Backend 개발 차단 해제

1번이 모든 Contract를 완성할 때까지 다른 Backend가 기다리면 안 된다.
Backend에서 인터페이스를 먼저 확정한다:

```java
public interface MaterialBlockchainService {
    String mint(...);
    String transfer(...);
    String burn(...);
    String consume(...);
}
```

| 단계 | 구현체 | 동작 |
| --- | --- | --- |
| 초반 | `MockBlockchainService` | 가짜 Tx Hash 반환 |
| 이후 | `RealBlockchainService` | 실제 Contract / Web3 연동 (교체만 하면 됨) |

이렇게 하면 3 · 4 · 5번이 Blockchain 담당자를 기다리지 않고 개발할 수 있다.

---

## 9. 초반 2주 일정

| 시기 | 담당 | 내용 |
| --- | --- | --- |
| Day 1 | 전체 | 서비스 Flow 확정, 기능 정책 확정, 역할 확정 |
| Day 2 | 전체 | ERD, API Convention, Branch Convention, Contract Interface |
| Day 3~4 | 1번 | Blockchain PoC |
| | 2번 | React 공통 구조 |
| | 3번 | Trade 설계 |
| | 4번 | Spring 공통 구조 |
| | 5번 | Production 설계 |
| | 6번 | EC2 / Jenkins / Docker |
| Day 5~ | 전체 | Material 등록 E2E 연결 (Vertical Slice) |
| 그 이후 | 각자 | 1: Material, 2: Journey, 3: Trade, 4: Processing, 5: Production, 6: User — 병렬 개발 |

---

## 10. 처음부터 하지 말아야 할 것

- ❌ 전체 화면부터 다 만들기
- ❌ ERD 확정 전에 Entity 만들기
- ❌ Blockchain 담당자가 모든 Contract를 먼저 완성하려고 하기
- ❌ Infra 담당자가 완벽한 CI/CD부터 만들기
- ❌ Material 구조가 안 정해진 상태에서 Marketplace부터 구현하기
- ❌ 6명이 서로 API 구조 다르게 만들기
- ❌ AI 기능 고민하느라 핵심 기능 늦추기

지금은 **기능 수를 늘리는 게 아니라 기반을 빨리 검증하는 시기**다.

---

## 11. 실행 체크리스트

당장 내일부터 이 순서대로 진행한다.

- [ ] 1. Material → Product 전체 Flow 화이트보드 작성
- [ ] 2. Material / Trade / Processing / Production 상태와 수량 정책 확정
- [ ] 3. ERD 1차 작성
- [ ] 4. API + Contract Interface 작성
- [ ] 5. React / Spring / Blockchain / Infra Skeleton 동시 구축
- [ ] 6. Material 등록 기능 하나를 React → Spring → DB → Blockchain 끝까지 연결
- [ ] 7. 성공하면 각 도메인으로 분산 개발
