# [공지] test 환경 material_type enum 재정렬 마이그레이션 수동 적용 (2026-09-19)

## 무슨 일이 있었나

`f3bd0a4`(backend/develop)에서 `MaterialType`이 `DENIM, COTTON, LEATHER, LINING, METAL,
MIXED, ETC` → `COTTON, COTTON_MIXED, DENIM, POLYESTER, SILK, VISCOSE, WOOL, ETC`로
바뀌었고, 같은 커밋에 `src/main/resources/db/migrations/2026-09-18-material-type-fiber-realignment.sql`
(구 값을 옮기는 UPDATE-only 마이그레이션)이 같이 들어왔습니다.

이 마이그레이션은 컬럼 구조를 바꾸지 않아 `scripts/check-schema.sh`(스키마 구조 대조)에
안 걸렸고, `test-deploy.sh`의 자동 적용 게이트도 "스키마 불일치일 때만" 열리게 되어 있어서
`cft-mysql`(test 스택)에 **적용되지 않은 채** 배포·기동이 계속 성공했습니다. `material_type`은
DB ENUM이 아니라 VARCHAR(50)이라 아무 제약에도 안 걸렸고, 남아있던 구 값(LEATHER/LINING/
METAL/MIXED)이 있는 행을 JPA가 읽는 순간 `IllegalArgumentException` → 500이 났습니다
(`GET /api/v1/listings` 등 material을 조인하는 모든 조회 경로).

## 무엇을 했나

1. `cft-mysql` mysqldump 백업 후 해당 마이그레이션을 수동 적용 (`material`,
   `processing_output`, `processor_lead_time`, `processor_capability` 네 테이블).
   - `processor_lead_time`(9건), `processor_capability`(3건)에서 옮기려는 구 값이
     해당 processor의 기존 ETC 행과 UNIQUE 제약 충돌 — **충돌한 구 값 행은 삭제**하고
     기존 ETC 행을 유지했습니다 (능력/리드타임 자체 손실 없음, 중복 등록만 정리).
   - `deploy_migrations_applied`에 기록 완료.
2. `scripts/test-deploy.sh`의 자동 마이그레이션 게이트를 "스키마 불일치 또는 미적용
   마이그레이션 파일 존재" 둘 중 하나만 참이어도 열리도록 수정 — 앞으로 UPDATE-only
   마이그레이션도 스키마 diff 없이 자동 적용됩니다. (README.md 배포 섹션 갱신)

## 다른 파트에 영향

- **로컬 DB를 예전에 시드했다면 같은 상태일 수 있습니다.** 로컬 backend를 그대로 띄우면
  같은 500이 재현됩니다 — 로컬에서도 위 마이그레이션 파일을 한 번 적용해 주세요.
- 이 처리는 `cft-mysql`(test)에만 했습니다. **운영 DB는 대상이 아닙니다** — 운영에
  같은 구 값이 남아있는지는 별도 확인이 필요합니다.

## 확인 필요 (인프라→백엔드)

- `processor_lead_time`/`processor_capability`의 UNIQUE 충돌로 구 값 행을 삭제한 판단이
  맞는지 검토 부탁드립니다 (다른 해석이 필요하면 알려주세요 — 백업은 남아 있습니다).
- 이번 스프린트 세 번째 같은 유형(NULL 허용 2건 + enum 값 1건)이라는 지적대로, 데이터
  전용 마이그레이션이 배포 파이프라인에서 구조 변경 마이그레이션과 동일하게 취급되도록
  이번에 고쳤습니다. 앞으로도 값만 옮기는 마이그레이션을 작성할 때 UNIQUE 제약 충돌
  가능성을 마이그레이션 자체에서 처리해 주시면(예: 충돌 시 DELETE 후 UPDATE) 이런 수동
  개입이 줄어들 것 같습니다.
