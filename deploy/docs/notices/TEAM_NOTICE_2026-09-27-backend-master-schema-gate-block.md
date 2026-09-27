# [공지] backend/master #15 빌드 실패 — 운영 스키마 대조 게이트가 배포를 막음 (2026-09-27)

## 무슨 일이 있었나

`09416a27`(backend/master, AI 상담 기능)이 병합됐고, MR 자체의 CI(테스트 2250건)는
전부 통과했습니다. 하지만 병합 커밋에서 도는 `backend/master` 빌드 **#15의 Deploy
스테이지**가 실패했습니다 — 이 커밋이 `agent_conversation`, `agent_message`,
`agent_pending_action` 세 테이블을 새로 기대하는데, 운영 DB(`cf-mysql`)에는 아직
해당 테이블이 없었기 때문입니다.

이건 `check-schema.sh` preflight 게이트가 **의도대로** 작동한 것입니다. 그대로
배포했다면 `ddl-auto: validate`인 Hibernate가 기동 단계에서 검증에 실패해 컨테이너가
죽고, 헬스체크 실패로 자동 롤백됐을 것입니다(새 기능은 못 나가고 원인 파악에 로그를
봐야 하는 상황).

원인은 새 엔티티에 대응하는 DDL이 backend의 schema.sql에는 정의돼 있었지만, 운영
DB에는 아직 수동 적용되지 않았던 것 — `/test` 스택(develop 기준)에는 이미 반영돼
있었을 수 있는 것과 별개로, **운영 배포(master) 전에는 운영 DB에 별도로 적용해야
한다**는 절차가 이번에 다시 드러났습니다.

## 무엇을 했나

1. `cf-mysql` 수동 백업 (`backup-mysql.sh`)
2. `SCHEMA_REF=origin/backend/master bash scripts/check-schema.sh --ddl`로 생성한
   DDL(테이블 3개 CREATE)을 운영 DB에 적용
3. `check-schema.sh` 재실행 → OK 확인
4. Jenkins에서 `backend/master` 빌드 수동 재실행(#16) → SUCCESS, `/api/health` 200,
   전체 컨테이너 healthy 확인

## 다른 파트에 영향

- 백엔드 코드는 건드리지 않았습니다. 운영 DB 스키마만 backend가 이미 정의한 대로
  맞췄습니다.
- **MR의 초록불은 병합 커밋의 후속 빌드까지 보장하지 않습니다** (`INFRA_SETUP.md`
  4장에 이미 있는 경고와 동일 유형 — 이번엔 develop이 아니라 master에서 발생).
  MR이 초록이어도 병합 후 GitLab Repository → Commits에서 해당 브랜치 최신 커밋
  상태를 한 번 더 확인하는 습관이 필요합니다.

## 확인 필요 (인프라→백엔드)

- 새 테이블을 요구하는 마이그레이션/엔티티가 `develop`에서 `master`로 갈 때, 운영 DB
  적용 시점을 어떻게 맞출지 합의가 필요합니다. 후보:
  - PR/MR 설명에 "운영 DB DDL 적용 필요" 여부를 명시
  - 또는 `deploy-service.sh`가 스키마 불일치를 감지하면 배포를 막는 대신 (지금처럼)
    사람이 알아채고 수동 적용하는 현재 흐름을 계속 유지
  - 이번 건은 이미 해결됐고 데이터 영향 없음 — 다음에 같은 유형이 또 나올 때
    대응 속도를 높이기 위한 논의입니다.
