# Material 구성 · 발생 사유 데이터 모델 확정

- 티켓: S15P21E104-435 (`[COMMON]`), 구현은 S15P21E104-436 (`[BE]`) · S15P21E104-437 (`[FE]`)
- 결정자: 공병우 (Material 관리)
- 날짜: 2026-09-07

## 문제

`-152` ([FE] Material 등록 Form) 설명이 **"구성"** 과 **"발생 사유"** 입력을 요구한다.
그런데 이 두 항목이 **Jira 다른 티켓 · `schema.sql` · API 명세 · 기획 정리본 어디에도 없다.**
저장할 곳이 없어 등록 폼에서 두 칸을 빼 둔 상태다.

## 결정 1 — 구성은 별도 테이블

혼방 비율이다. "면 60% / 폴리에스터 40%" 처럼 **한 소재에 여러 값**이 붙으므로
`material` 컬럼 하나로 끝나지 않는다.

```sql
CREATE TABLE material_composition (
    id BIGINT NOT NULL AUTO_INCREMENT,
    material_id BIGINT NOT NULL,
    fiber_type VARCHAR(50) NOT NULL,
    percentage DECIMAL(5, 2) NOT NULL,
    CONSTRAINT pk_material_composition PRIMARY KEY (id),
    CONSTRAINT uk_material_composition_fiber UNIQUE (material_id, fiber_type),
    CONSTRAINT fk_material_composition_material
        FOREIGN KEY (material_id) REFERENCES material (id),
    CONSTRAINT chk_material_composition_percentage
        CHECK (percentage > 0 AND percentage <= 100),
    INDEX idx_material_composition_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

### 왜 JSON 컬럼이 아닌가

`material.composition JSON` 이 더 간단해 보이지만 이 값은 **조회 조건이 된다.**
"면 50% 이상인 소재" 같은 검색과, Upcycle Ratio 계산에서의 집계가 예정돼 있다.
MySQL JSON 은 인덱스를 붙이려면 generated column 을 또 만들어야 해서 결국 컬럼이 는다.
테이블로 두면 인덱스가 그냥 붙는다.

### 왜 합계 100 을 강제하지 않는가

`percentage > 0 AND <= 100` 만 걸고 **합계는 100 이하만** 서비스에서 검사한다.
정확히 100 을 요구하면 **모르는 부분을 지어내게 된다.** 수거 소재는 "면 60% 는 확실하고
나머지는 모른다" 가 흔한 상황인데, 그때 "기타 40%" 를 넣게 만들면 없는 정보를 만든 것이다.
빈 부분은 빈 채로 둔다. 화면은 합계가 100 미만이면 "나머지 미확인" 으로 표시한다.

합계 검사는 DB 로 못 한다 — 여러 행에 걸친 조건이라 `CHECK` 로 표현되지 않는다.
그래서 서비스가 본다. **이 사실을 테스트로 고정한다** (DB 가 막아 줄 거라 착각하면
직접 INSERT 하는 경로에서 새어 나간다).

### `fiber_type` 은 왜 VARCHAR 인가

ENUM 으로 고정하고 싶지만 섬유 종류는 계속 늘어난다(면·폴리·나일론·울·레이온·텐셀…).
ENUM 은 값 추가마다 DDL 이고, 그건 `ddl-auto: validate` 환경에서 배포 절차를 부른다.
대신 **애플리케이션에서 허용 목록을 강제**한다 — `grade` 가 `VARCHAR(50)` 인 채로
방치되어 "A급·상·Grade A" 가 섞일 위험을 이미 안고 있는 상태이므로(`08-divergences.md`),
같은 실수를 반복하지 않는다. 목록 관리 주체는 코드다.

## 결정 2 — 발생 사유는 ENUM + 보조 메모

```sql
ALTER TABLE material
    ADD COLUMN origin_reason ENUM(
        'DEADSTOCK',
        'DEFECT',
        'OFFCUT',
        'END_OF_LIFE',
        'COLLECTED',
        'ETC'
    ) NULL,
    ADD COLUMN origin_reason_note VARCHAR(200) NULL;
```

| 값 | 뜻 |
| --- | --- |
| `DEADSTOCK` | 팔리지 않고 남은 재고 원단 |
| `DEFECT` | 불량·B급으로 정상 유통에서 빠진 것 |
| `OFFCUT` | 재단 후 남은 자투리 |
| `END_OF_LIFE` | 사용을 마친 제품에서 나온 것 |
| `COLLECTED` | 수거·회수 경로로 모인 것 |
| `ETC` | 위에 없음 — `origin_reason_note` 필수 |

### 왜 자유 텍스트가 아닌가

`grade` 를 `VARCHAR(50)` 으로 둔 결과를 보면 안다. 같은 뜻을 다르게 적으면 필터도
정렬도 통계도 안 된다. 발생 사유는 **업사이클 비율 리포트의 분류축**이 될 값이라
표준화가 특히 중요하다.

`ETC` 를 두고 메모를 붙이는 이유는, 목록에 없는 경우를 억지로 다른 값에 밀어 넣는 것을
막기 위해서다. 밀어 넣으면 그 소재는 영구히 잘못 분류된다.

### `generation_type` 과 겹치지 않게 한 것

`material.generation_type` 에 이미 `PRODUCTION_SCRAP` 이 있다. 그래서 발생 사유
목록에 `PRODUCTION_SCRAP` 을 **넣지 않았다.** 같은 사실을 두 컬럼에 적으면 반드시
어긋나고, 어긋났을 때 어느 쪽이 맞는지 판정할 방법이 없다.

두 컬럼의 역할을 이렇게 나눈다.

- `generation_type` — **어떻게 생겼나** (최초 등록 / 가공 산출 / 생산 자투리)
- `origin_reason` — **왜 폐기물이 되었나** (재고 / 불량 / 자투리 / 수명 종료 / 수거)

### NULL 을 허용한 이유

두 컬럼 다 `NULL` 이다. 이미 등록된 소재가 있고 `ddl-auto: validate` 환경에서
`NOT NULL` 컬럼을 추가하면 기존 행 때문에 DDL 이 실패한다. 그리고 `root_origin=VIRGIN`
(신소재)인 소재는 발생 사유라는 개념 자체가 없다.

**`root_origin=WASTE` 일 때만 필수**로 하고, 그 검사는 서비스가 한다.

## API 변경

`POST /api/materials` 요청에 추가:

```json
"originReason": "DEADSTOCK",
"originReasonNote": null,
"composition": [
  { "fiberType": "COTTON", "percentage": 60 },
  { "fiberType": "POLYESTER", "percentage": 40 }
]
```

`GET /api/materials/{id}` 응답에 같은 모양으로 포함. `composition` 은 정보가 없으면
**빈 배열**이고 `null` 이 아니다 — 화면이 분기를 두 번 하지 않게 한다.

검증:

- `composition` 합계 ≤ 100, 각 항목 `percentage > 0`
- `fiberType` 중복 금지 (DB 제약과 동일한 조건을 앞에서 잡아 500 대신 400 을 준다)
- `root_origin=WASTE` 이면 `originReason` 필수
- `originReason='ETC'` 이면 `originReasonNote` 필수
- `originReason != 'ETC'` 인데 note 가 오면 **거절하지 않고 저장**한다 — 부가 설명은
  어느 값에서도 쓸모가 있다

## 구현 순서 (S15P21E104-436 → -437)

1. `schema.sql` 에 `material_composition` + `material` 두 컬럼. **`check-schema.sh` 로
   라이브 DB 와 대조 → DDL 적용 → 확인** 후에 코드를 올린다 (배포 순서 계약)
2. 엔티티 · 스키마 매핑 테스트 (테스트 프로파일이 `create-drop` 이라 어긋남을 못 잡는다 —
   매핑 테스트가 유일한 방어선)
3. 등록·조회 서비스와 DTO
4. `-437` 에서 등록 폼과 상세 화면에 붙인다

## 되돌릴 수 있게 남겨 둔 것

`fiber_type` 허용 목록과 `origin_reason` 값 목록은 **기획 확정이 아니라 내 판단**이다.
도메인 문서에 근거가 없어서 폐기물 업사이클의 통상 분류를 따랐다.
기획 쪽 정본이 나오면 `origin_reason` 은 ENUM 이라 DDL 이 필요하니 **그 전에 확정**하는
편이 낫다. 이 문서를 팀에 공유해 이견을 받는다.
