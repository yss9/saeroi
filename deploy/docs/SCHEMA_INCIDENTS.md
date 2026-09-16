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
- **test 스택 한정 안전 자동 적용** (`test-deploy.sh`) — "없는 테이블만 있고, CREATE
  TABLE만으로 된 마이그레이션"인 경우에 한해 적용 전 자동 백업 후 자동 적용. `ALTER`가
  하나라도 섞이면 자동 적용하지 않고 배포 자체를 막는다. **운영에는 자동 적용을 넣지
  않았다** — `check-schema.sh`의 "사람이 보고 결정한다" 원칙은 운영에서 그대로 유지.
- **비교 기준 커밋의 신선도 보장** (`check-schema.sh`, 2026-09-16) — fetch 실패를 더
  이상 무시하지 않는다.

## 아직 자동화 안 된 것 (다음에 또 터질 수 있는 지점)

1. **`ALTER`가 섞인 마이그레이션은 여전히 전적으로 사람이 발견해야 한다.** 지금은
   "누군가 크래시루프를 보고 나서야" 발견한다 — 병합 직후 자동으로 알려주는 장치가 없다.
   - 개선안: Jenkins가 `backend/develop`을 빌드할 때마다(배포 여부와 무관하게)
     `check-schema.sh`를 test DB 기준으로 돌려 Mattermost로 알리는 별도 알림 스테이지.
     지금은 배포 시도(=크래시)가 있어야만 드러난다.
2. **마이그레이션 파일이 실제로 `schema.sql`이 선언한 목표 상태를 정확히 재현하는지
   자동으로 검증하는 장치가 없다.** 이번 `CANCELED` 누락처럼, 마이그레이션 작성자의
   실수(ENUM 값 빠뜨림 등)는 사람이 `check-schema.sh --ddl` 출력을 눈으로 읽어야만
   잡힌다.
   - 개선안: 빈 MySQL 컨테이너에 이전 `schema.sql` + 해당 브랜치의 모든
     `db/migrations/*.sql`을 순서대로 적용한 뒤, 그 결과를 새 `schema.sql`과
     컬럼 단위로 diff하는 CI 스텝. 이번 사고를 병합 **전에** 잡을 수 있었던 유일한 방법.
     backend 파트의 CI(Jenkinsfile)에 추가해야 하는 항목이라 deploy 단독으로는 못 만든다
     — backend 팀에 제안할 것.
3. **`check-schema.sh`가 보지 않는 항목**(NULL 허용, DEFAULT, 인덱스, 컬럼 순서,
   CHARACTER SET/COLLATE)은 의도적 사각지대다. Hibernate `validate`가 실제로 보는
   것만 좁게 잡는다는 원칙은 유지하되, 여기서 또 사고가 나면 "그 사각지대 중 어느
   것이 실제로 validate를 깨뜨리는지" 먼저 확인하고 범위를 넓힐 것.

관련 문서: `scripts/check-schema.sh` 상단 주석(더 상세한 배경), `docs/notices/`(팀 공지가
필요한 개별 사고는 여기).
