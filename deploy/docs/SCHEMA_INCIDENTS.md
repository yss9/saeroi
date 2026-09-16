# 스키마 사고 트러블슈팅 로그

`backend`는 `ddl-auto: validate`를 쓴다 — 스키마를 자동으로 만들거나 고쳐주지 않는다.
엔티티가 기대하는 테이블/컬럼/타입이 DB에 없으면 Hibernate가 기동 단계에서 검증에
실패하고 컨테이너가 그대로 죽는다. 이 문서는 그 특성 때문에 반복된 배포 사고를
시간순으로 정리하고, 무엇이 자동화됐고 무엇이 아직 사람 손을 타는지 기록한다.

프로젝트 마무리 시점에 "왜 이런 스크립트/구조가 생겼는지" 되짚어볼 용도로 남긴다.

---

## 왜 이런 사고가 반복됐는가 (공통 원인)

1. 스키마의 source of truth가 `backend`의 `schema.sql` + `db/migrations/*.sql`이고,
   실제 DB에 적용하는 것은 **deploy가 손으로/스크립트로** 하는 별도 단계다. 두 브랜치가
   분리돼 있어 "스키마가 바뀌었다"와 "DB에 반영됐다" 사이에 항상 시차와 수작업 지점이 있다.
2. backend의 테스트 프로파일은 `create-drop`이라 엔티티가 시키는 대로 테이블을 만든다.
   그래서 **테스트 수백 건이 전부 초록이어도 스키마 불일치는 절대 드러나지 않는다.**
   불일치는 오직 `validate` 프로파일(로컬/운영/test 배포)로 기동할 때만 나타난다.
3. 따라서 이 계열 사고는 항상 "CI는 초록인데 배포만 실패한다"는 형태로 온다.

---

## 사고 타임라인

| 날짜 | 사고 | 원인 | 조치 |
| --- | --- | --- | --- |
| 2026-09-07 | EC2 운영 DB가 테이블 0개 | 엔티티 0개 시절엔 `validate`가 통과해 아무도 몰랐다. 엔티티가 15개로 늘자 빈 DB에서 `validate`가 깨짐 | `schema.sql`을 35테이블 일괄 수동 적용. `scripts/check-schema.sh` 최초 작성(테이블·컬럼 이름만 대조) |
| 2026-09-08 | `backend/master #10` 배포 후 헬스체크 실패 → 롤백 | `blockchain_transaction.tx_hash`가 `schema.sql`엔 `CHAR(66)`인데 엔티티는 `@Column(length=66)`만 선언 → Hibernate는 `VARCHAR(66)`을 기대. **check-schema.sh가 "OK"를 냈던 배포였다** — 컬럼 이름만 보고 타입은 안 봤기 때문 | `check-schema.sh`에 **컬럼 타입 대조** 추가(`a516ac2`). 같은 날 `processing_job.evidence_hash`에서도 동일 유형 발견·수정 |
| 2026-09-14 | test 스택에서 `user_notification` 테이블 없음으로 backend 기동 실패 | 새 테이블 추가 마이그레이션이 test DB(`cft-mysql`)에 반영 안 된 채 배포됨 | `test-deploy.sh`에 **preflight** 추가(`f2aa01a`) — 배포 전 `check-schema.sh`로 대조하고, "없는 테이블만 있고 컬럼/타입 불일치가 전혀 없는" 안전한 경우에 한해 해당 CREATE TABLE 마이그레이션을 자동 적용. `ALTER`가 섞이면 사람에게 넘긴다 |
| 2026-09-16 | `cft-backend` 크래시루프 (`missing column [brand_material_lock_id]`) | 두 가지가 겹쳤다 — 아래 "2026-09-16 상세" 참고 | 교정 DDL 수동 적용 + `check-schema.sh` 자체의 fetch 버그 수정 |

---

## 2026-09-16 상세 — 이번 사고, 지금까지와 다른 점

이번 건은 이전 세 건과 달리 "수동 적용을 깜빡함"이 아니라 **자동화 장치(preflight) 자체가
false OK를 낸 사고**였다. 두 가지 독립된 문제가 겹쳐 있었다.

### 1) 마이그레이션 파일 자체의 버그 (backend 소유, 수정 안 함 — 알림만)

`backend/develop`의 `db/migrations/2026-09-15-processing-custody-transfer.sql`이
`processing_input.material_lock_id`를 `brand_material_lock_id`로 개명하고
`processing_job.status` ENUM을 재정의하는데, 그 재정의에서 **`CANCELED` 값이
빠졌다**(entity의 `ProcessingJobStatus`와 `schema.sql`은 11개 값, 마이그레이션은 10개).
이대로 적용했다면 이후 `CANCELED` 상태 데이터가 깨졌을 것이다. 다행히 test DB의
실제 데이터엔 `PAID`/`CONFIRMED`만 있어서 적용 시점엔 데이터 손실이 없었다.

- deploy가 직접 고치지 않았다 — 이 파일은 backend 파트 소유. test DB에는 `CANCELED`를
  포함하도록 **교정한 DDL**을 적용했지만, 레포의 마이그레이션 파일 자체는 그대로다.
- **후속 조치 필요**: backend 팀에 이 파일의 ENUM 버그를 알려야 한다
  (`backend/develop → backend/master` 승격 시 운영에도 그대로 들어간다).

### 2) `check-schema.sh`의 fetch 실패 무시 버그 (deploy 소유, 수정함)

Jenkins 빌드 로그(`backend/develop #115`, 커밋 `5fb70321`)를 보면 **check-schema.sh가
"OK"를 냈는데도 배포된 컨테이너는 곧바로 크래시루프**에 빠졌다. 두 결과가 모순된다 —
같은 커밋 `5fb70321`의 `schema.sql`엔 분명히 `brand_material_lock_id`가 있었다.

원인은 스크립트의 이 줄이었다:

```bash
git fetch -q origin "${SCHEMA_REF#origin/}" 2>/dev/null || true
```

`git fetch`가 실패해도(네트워크 hiccup, GitLab 응답 지연 등) `|| true`가 실패를 완전히
삼킨다. 그러면 바로 다음 줄 `git show "$SCHEMA_REF:$SCHEMA_PATH"`는 **deploy 체크아웃에
로컬로 남아 있던 예전 `origin/backend/develop` ref**를 그대로 기준 삼아 비교한다.
그 예전 커밋이 아직 `brand_material_lock_id`를 요구하지 않던 시점이면, 스크립트는
"OK"를 내면서도 실제로는 Jenkins가 그 순간 빌드하고 있던 최신 커밋과는 **다른 기준으로**
검증한 셈이 된다 — false OK.

두 git 작업(Jenkins의 소스 체크아웃 vs. `check-schema.sh`의 독립적인 `git fetch`)이
같은 순간 같은 커밋을 본다는 보장이 원래 없었다. 빌드 로그의 "OK" 자체가 이 레이스의
증거다.

**조치 (`scripts/check-schema.sh`, 커밋 완료)**: fetch 실패를 fatal로 바꿨다. 실패하면
"오래된 로컬 캐시 기준으로 잘못된 OK를 낼 수 있다"고 명시하고 exit 2. 또한 비교에 쓴
정확한 커밋 SHA를 항상 로그에 남기도록 했다 — 다음에 비슷한 모순(스크립트는 OK, 배포는
실패)이 생기면 이번처럼 Jenkins 빌드 로그를 뒤지지 않고 바로 "그 SHA가 실제로 뭘
기준으로 삼았는지" 확인할 수 있다.

**후속 — 같은 날 이 수정이 배포되자마자 실제로 걸린 사례**: `backend/develop #117` 빌드에서
fetch 가 정말로(일회성 네트워크 blip으로) 실패해 배포가 그대로 막혔다. 바로 수동으로 같은
`git fetch`를 돌려보니 즉시 성공 — 순간적인 blip이었다. "가짜 OK는 절대 안 된다"는 원칙은
유지하되, 이런 일회성 blip까지 매번 사람이 재배포하게 만드는 건 배보다 배꼽이 커서 fetch를
**최대 3회, 2초 간격으로 재시도**한 뒤에만 fatal 처리하도록 보강했다. 3회를 다 실패하면
여전히 진짜 문제(네트워크 단절, GitLab 다운 등)로 보고 막는다.

---

## 지금 자동화돼 있는 것

- **컬럼 이름 + 타입 대조** (`check-schema.sh`) — NULL 허용/DEFAULT/인덱스는 의도적으로
  대조하지 않는다(표기 방식이 다양해 거짓 양성이 쏟아지면 사람이 스크립트를 무시하게
  되고, 그게 더 위험하다).
- **비교 기준 커밋의 신선도 보장** (`check-schema.sh`, 2026-09-16) — fetch 실패를 더
  이상 무시하지 않는다(단, 일회성 blip 은 3회 재시도로 흡수).
- **병합 전 마이그레이션 검증 CI 게이트** (`scripts/verify-migrations.sh`, backend
  레포 소유, 2026-09-16) — 아래 "해소됨" 2번 항목.
- **test 스택 전면 자동 적용** (`test-deploy.sh`, 2026-09-16) — 아래 "해소됨" 1번 항목.

## 해소됨 (2026-09-16, CI 게이트 도입 이후)

1. **`ALTER` 포함 마이그레이션도 test 스택엔 이제 전부 자동 적용된다.** 전에는 "없는
   테이블만 있고 CREATE TABLE만으로 된" 안전한 경우만 자동 적용하고 나머지는 사람이
   SSH로 들어가 DDL을 손으로 적용해야 했다 — backend가 병합될 때마다 test 백엔드가
   크래시루프에 빠지고 사람이 수동 개입해야 하는 상황이 반복됐다(이 문서에 기록된 사고
   전부가 그 패턴). 아래 2번(병합 전 CI 게이트)이 생기면서, backend/develop에 있는
   마이그레이션 파일은 "적용하면 schema.sql 목표 상태와 정확히 같아진다"가 이미 병합
   시점에 검증된 것이므로, ALTER 여부와 무관하게 test 환경에 한해 신뢰하고 자동
   적용하도록 `test-deploy.sh`를 다시 짰다. 무엇을 이미 적용했는지는 Flyway 없이
   `cft-mysql` 안의 `deploy_migrations_applied` 테이블로 직접 추적한다. 적용 전
   mysqldump 백업은 그대로 유지. **운영은 여전히 수동이다** — 실패 시 롤백 절차가 test와
   다르고(사용자 데이터 영향), 이 부분은 "사람이 보고 결정한다" 원칙을 유지하기로 했다.
2. **마이그레이션 파일이 실제로 `schema.sql`이 선언한 목표 상태를 정확히 재현하는지
   병합 전에 자동 검증한다** (`scripts/verify-migrations.sh`, backend Jenkinsfile의
   "마이그레이션 스키마 대조" 스테이지). 빈 MySQL 컨테이너에 베이스라인 `schema.sql` +
   새 마이그레이션을 적용한 결과를, 지금(HEAD) `schema.sql`을 그대로 적용한 결과와
   컬럼 단위로 diff한다. 이 문서의 `CANCELED` 누락 사고로 실제 검증 완료 — 처음엔 FK
   rename에 필요한 `ALGORITHM=INPLACE` 누락으로, 고친 뒤엔 `CANCELED` 누락으로 정확히
   막혔고, 둘 다 고친 버전은 diff가 완전히 비어 통과했다. 첫 실행에서 Jenkins 워크스페이스의
   git 인증 문제로 한 번 더 걸렸다가(`gitlab-token` credential을 `withCredentials`로
   묶어 해결) 지금은 안정적으로 통과한다.

## 아직 자동화 안 된 것 (다음에 또 터질 수 있는 지점)

1. **운영(`cf-mysql`)은 여전히 전적으로 수동이다.** CI 게이트가 마이그레이션의 정확성은
   보장하지만, 운영 DB에 적용하는 시점과 방식(트래픽이 있는 상태에서의 락 영향 등)은
   여전히 사람이 판단해야 한다는 게 이번 결정이다. 나중에 운영도 자동화할지는 별도 논의.
2. **`check-schema.sh`가 보지 않는 항목**(NULL 허용, DEFAULT, 인덱스, 컬럼 순서,
   CHARACTER SET/COLLATE)은 의도적 사각지대다. Hibernate `validate`가 실제로 보는
   것만 좁게 잡는다는 원칙은 유지하되, 여기서 또 사고가 나면 "그 사각지대 중 어느
   것이 실제로 validate를 깨뜨리는지" 먼저 확인하고 범위를 넓힐 것.
3. **CI 게이트를 우회한 커밋**(예: 보호 규칙 없이 직접 push)은 여전히 test 자동 적용
   단계에서 걸러지지 않는다 — `test-deploy.sh`는 자동 적용 후 `check-schema.sh`를 한 번
   더 돌려 방어선을 하나 더 두긴 했지만, 애초에 틀린 마이그레이션이 이미 적용된 뒤라
   백업에서 복구해야 한다.

관련 문서: `scripts/check-schema.sh` 상단 주석(더 상세한 배경), `docs/notices/`(팀 공지가
필요한 개별 사고는 여기).
