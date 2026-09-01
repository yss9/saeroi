# 📢 인프라 공지 — 세 파트 모두 실제 이미지로 배포됨 (2026-09-01)

## 결론부터

**backend / frontend / ai 세 파트 모두 CD 전 구간을 통과했고, placeholder는 전부 사라졌습니다.**
`docker-compose.placeholder.yml` 파일 자체를 삭제했습니다.

| 파트 | 상태 | 확인 내용 |
| --- | --- | --- |
| backend | 실제 이미지 | `https://j15e104.p.ssafy.io/api/health` → 200, MySQL 연결됨 |
| frontend | 실제 이미지 | 실제 Vite 빌드 결과 서빙 (`<title>새로이</title>`) |
| ai | 실제 이미지 | backend 에서 `http://ai:8000/health` → `{"status":"UP"}` |

**backend → ai 내부 호출이 처음으로 실증됐습니다.** 네트워크 격리 설계(`internal`)가 의도대로
동작한다는 뜻이기도 합니다.

## 각 파트에 알려드릴 것

### 2번 (Frontend)

`frontend/develop` 을 `frontend/master` 로 머지해 CD를 돌렸습니다. **여러분이 작성한 코드 그대로**이고
제가 추가한 코드는 없습니다. 이제 `master` 에 머지할 때마다 자동 배포됩니다.

`VITE_API_BASE_URL` 은 EC2 `.env` 에 **`/api`** 로 넣어뒀습니다. 프론트와 백엔드가 같은 도메인
뒤에 있으므로 절대 URL이 필요 없습니다. 다른 값이 맞다면 알려주세요 — 바꾸면 **재빌드해야** 반영됩니다.

나머지 `VITE_*`(Besu RPC, 컨트랙트 주소 3종)는 여전히 **빈 값**입니다. 1번 산출물 대기 중이고,
값이 들어오면 재빌드가 필요합니다.

### 5번 (AI)

CD 검증을 위해 `ai/develop` 에 **최소 스캐폴드**를 넣었습니다. 실제 추론 로직이 준비되면
**통째로 교체하셔도 됩니다.**

```
app/main.py        FastAPI — GET /health, GET /
tests/test_main.py pytest 2건
requirements.txt   fastapi, uvicorn, httpx
```

지켜야 할 인프라 계약은 둘뿐입니다.

- **포트 8000** — backend 가 `AI_SERVER_BASE_URL=http://ai:8000` 으로 호출합니다.
- **이미지 이름** `circular-fashion/ai:latest` — Jenkinsfile 이 이미 그렇게 태깅합니다.

같이 머지된 MR !28 은 Detect 스테이지의 경로 버그 수정입니다. 이게 없었으면 `requirements.txt` 를
추가해도 `HAS_DOCKERFILE=false` 로 남아 **빌드는 초록인데 배포는 영원히 스킵**됐습니다.
이번 빌드 로그의 `deps=true tests=true dockerfile=true` 가 수정이 동작한다는 증거입니다.

### 1번 (Blockchain)

변경 없습니다. `BESU_RPC_URL` / `BESU_CHAIN_ID` / 컨트랙트 주소 3종을 계속 기다리고 있습니다.
환경변수 이름을 `BESU_*` 로 통일하는 건에 대한 회신도 부탁드립니다.

## 배포 방법 (이제 세 파트 모두 동일)

```
<파트>/develop 에서 CI 초록 확인
      ↓
<파트>/develop → <파트>/master 머지
      ↓
Jenkins 가 자동으로: 테스트 → 이미지 빌드 → 배포 → 헬스체크
      ↓
헬스체크 실패 시 직전 이미지로 자동 롤백
```

placeholder 졸업 절차는 **더 이상 필요 없습니다.** 세 파트 다 졸업했습니다.

## 구조 변경 요약

- `docker-compose.placeholder.yml` 삭제. 그 안에 있던 mysql 루프백 바인딩
  (`127.0.0.1:3306:3306`)은 `docker-compose.yml` 본체로 옮겼습니다 — 이건 placeholder 가 아니라
  **DB 외부 노출을 막는 유일한 수단**이라 반드시 유지해야 합니다.
- `scripts/compose.sh` 의 `dc()` 는 그대로 유지합니다. override 파일이 없으면 base 만 쓰도록
  이미 되어 있고, compose 호출 경로를 하나로 유지하는 값어치가 있습니다.
- 백업을 EC2 밖으로 반출하는 `scripts/pull-backups.sh` 를 추가했습니다.
  **팀원 로컬에서** 실행합니다 (EC2 에서 돌리는 스크립트가 아닙니다).

## 지금 서비스 주소

| | |
| --- | --- |
| 서비스 | https://j15e104.p.ssafy.io |
| Jenkins | https://j15e104.p.ssafy.io/jenkins/ (8081 직접 접속은 막혀 있습니다) |
