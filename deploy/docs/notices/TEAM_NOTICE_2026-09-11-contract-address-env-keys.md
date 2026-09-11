# [공지] 컨트랙트 주소 환경변수 키 이름이 잘못돼 있었습니다 (2026-09-11)

작성: 6번(인프라) · 대상: 전 파트, 특히 Backend·Frontend·Blockchain

## 무엇이 문제였나

`.env.example`의 컨트랙트 주소 키가 `PAYMENT_TOKEN_ADDRESS`/`ESCROW_ADDRESS`
(`VITE_PAYMENT_TOKEN_ADDRESS`/`VITE_ESCROW_ADDRESS`)로 남아 있었는데, 이건 **실제로
backend/frontend 어느 쪽도 읽는 이름이 아니었습니다** — 초기 기획 문서에 있던 이름이
그대로 방치된 죽은 키였습니다.

- backend: `application.yml`이 실제로 읽는 건 `MATERIAL_TOKEN_ADDRESS` /
  `MARKETPLACE_ADDRESS` / `PRODUCTION_REGISTRY_ADDRESS` 세 개뿐입니다
  (`app.blockchain.*`). Escrow는 쓰지 않습니다.
- frontend: `docker/Dockerfile`의 실제 `ARG`는 `VITE_MATERIAL_CONTRACT_ADDRESS` /
  `VITE_MARKETPLACE_CONTRACT_ADDRESS` / `VITE_PROCESSING_CONTRACT_ADDRESS` /
  `VITE_PRODUCTION_CONTRACT_ADDRESS`입니다. 선언 안 된 build-arg는 Docker가 경고만
  내고 값을 버려서, 채워 넣어도 번들에는 빈 문자열이 들어갑니다 — "안 채운 것"과 증상이
  같아 구분이 안 됩니다.

## 수정 내용

`deployments/ssafy.json`(`blockchain/develop`)의 실제 배포 주소를 기준으로
`docker-compose.yml`(운영)·`docker-compose.test.yml`(test)·`.env.example` 모두
올바른 키 이름으로 정정하고 값을 채웠습니다.

| 컨트랙트 | 주소 |
| --- | --- |
| MaterialToken | `0xBE98f5A9aA8d0480B09C689e38806aCcD41a0f66` |
| Marketplace | `0x53Fc91Be37720Fcb051004B7aab73639CA266853` |
| ProcessingRegistry | `0x8C4b9e2bDEb53D7120Df681E891A7A6D9E1ecB5E` (frontend 전용, backend 미사용) |
| ProductionRegistry | `0x65B4D067E929a7Ce64E8d5896bD8D4e0efE6DD3a` |

## 확인해 주세요

- **frontend는 재빌드가 필요합니다** (`VITE_*`는 빌드 시점 인라인 — 값만 채워서는
  반영 안 됩니다). `frontend/master`·`frontend/develop` 다음 빌드부터 반영됩니다.
- Blockchain 담당자: 이 주소가 현재 `ssafy.json`과 일치하는지, 재배포 계획이 있다면
  미리 알려주세요 — 재배포하면 주소가 바뀌고 프론트 재빌드가 다시 필요합니다.
- 이 키 이름으로 이미 코드에서 다르게 참조하고 있는 곳이 있다면 알려주세요.
