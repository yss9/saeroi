# 📢 인프라 공지 — CD 경로 첫 통과 (2026-09-01)

## 요약

**backend 가 CD 전 구간을 처음으로 통과했습니다.** 이미지 빌드 → 배포 → 헬스체크가 실제로 돌았고,
`https://j15e104.p.ssafy.io/api/health` 가 200 을 반환합니다. backend 는 이제 placeholder 가 아니라
실제 Spring Boot 컨테이너입니다 (MySQL 연결 확인됨).

frontend / ai 는 아직 placeholder 입니다.

## 각 파트가 첫 배포할 때 밟는 절차

1. `<파트>/develop` 에서 CI 초록 확인
2. `<파트>/develop` → `<파트>/master` MR 머지 → Jenkins 가 이미지 빌드
3. **Deploy 스테이지가 "아직 placeholder 로 실행 중"이라며 실패합니다.** 정상입니다.
   이때 6번에게 알려주시면 `docker-compose.placeholder.yml` 졸업 MR 을 올리고 EC2 에 반영합니다.
4. 파이프라인 재실행 → 통과

## 헬스체크 규칙이 바뀌었습니다 (파트에 영향 있음)

첫 CD 를 돌려보니 **기존 헬스체크가 가짜였습니다.** `curl -f` 는 3xx 를 실패로 보지 않는데
nginx 80 포트는 443 으로 301 리다이렉트만 하므로, 앱에 닿지도 않고 "OK -> 301" 이 찍히고 있었습니다.
고친 내용:

- **2xx 만 통과합니다.** 3xx 도 실패입니다.
- 컨테이너 기동을 **최대 90초 기다립니다** (2초 간격 재시도). 예전에는 컨테이너가 뜬 지 1초 만에
  검사해서, 정상 배포도 실패로 찍힐 수 있는 구조였습니다.
- Jenkins 안에서 돌 때는 오리진(`http://backend:8080`, `http://frontend:80`)도 함께 검사합니다 —
  nginx 문제와 앱 문제를 로그에서 바로 구분할 수 있습니다.

파트별로 지켜야 할 계약:

| 파트 | 배포 성공 조건 |
| --- | --- |
| backend | `GET /api/health` 가 **인증 없이 2xx** (구현 완료) |
| frontend | `GET /` 가 2xx |
| ai | internal 전용이라 컨테이너 running 상태만 확인 |

## 배포 실패 시 자동 롤백이 생겼습니다

헬스체크가 실패하면 직전 이미지로 되돌리고 재기동합니다. 예전에는 깨진 컨테이너가 그대로
트래픽을 받았습니다. 단, 직전 컨테이너가 `circular-fashion/<파트>` 이미지일 때만 롤백합니다
(placeholder 로 되돌리면 졸업 가드가 통과해 placeholder 가 정상 배포로 위장되기 때문입니다).

## ⚠️ 2번(Frontend) — 첫 배포 전에 반드시 확인

frontend Docker Build 는 EC2 `.env` 에서 `VITE_*` 줄을 긁어 `--build-arg` 로 넘깁니다.
그런데 **값이 비어 있어도 줄만 있으면 빌드는 그냥 성공합니다.** 빌드가 막아주지 않습니다 —
빈 문자열이 번들에 그대로 인라인된 채 조용히 배포됩니다.

현재 EC2 `.env` 의 `VITE_*` 는 **전부 빈 값**입니다:

```
VITE_API_BASE_URL, VITE_BESU_RPC_URL, VITE_BESU_CHAIN_ID,
VITE_MATERIAL_TOKEN_ADDRESS, VITE_MARKETPLACE_ADDRESS, VITE_ESCROW_ADDRESS
```

- `VITE_API_BASE_URL` 은 블록체인과 무관하게 **지금 정할 수 있습니다.** 프론트 axios 설정에 맞춰
  `/api` 인지 `https://j15e104.p.ssafy.io/api` 인지 알려주시면 6번이 EC2 `.env` 에 넣습니다.
- 나머지는 1번 산출물 대기입니다.
- `VITE_*` 는 **빌드 시점에 번들로 인라인**됩니다. 값이 바뀌면 반드시 frontend 를 재빌드해야 합니다.

## ⚠️ 5번(AI) — MR 승인 부탁드립니다

[MR !28](https://lab.ssafy.com/s15-blockchain-pred-sub1/S15P21E104/-/merge_requests/28)

`ai/develop` 의 Jenkinsfile Detect 스테이지가 `fileExists('Dockerfile')` 을 보는데
실제 파일은 `docker/Dockerfile` 입니다. 이대로면 `requirements.txt` 를 추가해도
`HAS_DOCKERFILE` 이 계속 false 라서 **빌드는 초록인데 Docker Build 와 Deploy 가 조용히 스킵**됩니다.
한 줄 수정이며, 파트 소유 파일이라 직접 고치지 않고 MR 로 올렸습니다.

## ⚠️ 1번(Blockchain) — 환경변수 이름 통일 합의

같은 값에 이름이 세 벌입니다:

| 용도 | deploy/master `.env.example` | blockchain/develop `.env.example` |
| --- | --- | --- |
| RPC URL | `BESU_RPC_URL` | `SSAFY_RPC_URL` |
| Chain ID | `BESU_CHAIN_ID` | `SSAFY_CHAIN_ID` |
| 배포 키 | `BLOCKCHAIN_SERVICE_PRIVATE_KEY` | `DEPLOYER_PRIVATE_KEY` |

컨트랙트 어휘도 안 맞습니다 — 프론트는 `VITE_MATERIAL_TOKEN_ADDRESS` / `VITE_MARKETPLACE_ADDRESS` /
`VITE_ESCROW_ADDRESS` 를 기대하는데 blockchain 쪽 산출물은 `paymentToken` 을 이야기합니다.

**제안: `BESU_*` 로 통일하고, 컨트랙트 이름은 1번이 확정해 주시면 프론트/백엔드 키를 거기에 맞춥니다.**
`BESU_RPC_URL`, `BESU_CHAIN_ID`, 컨트랙트 주소가 나오면 6번에게 전달 부탁드립니다.

## 참고

- 서비스 https://j15e104.p.ssafy.io · Jenkins http://j15e104.p.ssafy.io:8081
- 이미지 규약 `circular-fashion/<part>:latest` (변경 없음)
- 2026-08-31 공지(`TEAM_NOTICE_2026-08-31.md`)의 CI/CD 동작 설명은 그대로 유효합니다.
