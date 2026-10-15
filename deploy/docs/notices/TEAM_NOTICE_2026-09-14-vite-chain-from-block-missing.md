# [공지] `VITE_CHAIN_FROM_BLOCK`이 `.env.example`에 빠져 있었습니다 (2026-09-14)

작성: 6번(인프라) · 대상: Frontend, Blockchain

## 무엇이 문제였나

소재 등록 화면에서 MetaMask 서명 확인까지 시간이 매우 오래 걸리는 문제가 재발했습니다.

frontend의 `b95e121 (fix(blockchain): 중복 발행 가드가 체인 전체를 훑지 않게 한다)`에서
`VITE_CHAIN_FROM_BLOCK`이라는 새 build-time 환경변수를 도입했지만:

1. **`docker/Dockerfile`에 `ARG VITE_CHAIN_FROM_BLOCK` 선언이 없었습니다** — frontend
   MR !314 (`frontend/fix/vite-chain-from-block-docker-arg`)로 정정, `frontend/develop`
   병합 완료.
2. **인프라 쪽 `.env.example`에도 이 키 자체가 없었습니다.** frontend 저장소의
   `.env.example`에는 있었지만, 실제 배포에 쓰는 이 저장소(`deploy`)의 `.env.example`과
   EC2 `/home/ubuntu/deploy/.env`에는 반영되지 않아 값이 비어 있었습니다.

두 가지가 겹쳐서, ARG를 고쳐도 `.env`에 값이 없으면 Jenkins가 build-arg 자체를 넘기지
않아 여전히 `fromBlock=0`으로 폴백 — 제네시스 블록부터 전체 스캔이 계속됐습니다.

## 수정 내용

- `.env.example`에 `VITE_CHAIN_FROM_BLOCK=11221171` 추가
- EC2 `.env`에 동일 값 반영
- `docs/INFRA_SETUP.md` 3장 환경변수 표에 반영

값(`11221171`)은 `blockchain/develop:deployments/ssafy.json`의 MaterialToken
`deployedAt`(2026-09-08T02:11:04Z)을 `eth_getBlockByNumber` 이진 탐색으로 대조해
실측했습니다 (해당 시각 근처 블록 11,221,174 — 서영석 님이 커밋 메시지에 남긴
`11221171`과 사실상 같은 지점).

## 확인해 주세요

- **frontend/develop 재빌드가 필요합니다** — `VITE_*`는 빌드 시점 인라인이라 `.env`만
  바꿔서는 반영되지 않습니다. `/test` 스택 Test Deploy를 한 번 더 돌려주세요.
- Blockchain 담당자: 컨트랙트 재배포 계획이 있으면 이 블록 번호도 같이 갱신해야 합니다
  (재배포하면 로그 시작 지점이 바뀝니다).
- 앞으로 `VITE_*` 키를 새로 추가할 때는 **frontend `docker/Dockerfile`의 `ARG`** 와
  **이 저장소의 `.env.example`/실제 `.env`** 양쪽에 같은 커밋 사이클에서 반영해 주세요 —
  한쪽만 되면 조용히 느려지거나 깨지는데 겉으로는 티가 안 납니다.
