# VITE_CHAIN_FROM_BLOCK Dockerfile ARG 누락 수정 + Test Deploy 스테이지 검사 추가

작성: Infra(6번), 2026-09-14 · 관련 커밋: `b95e121`

## 무엇이 문제였나

`b95e121 (fix(blockchain): 중복 발행 가드가 체인 전체를 훑지 않게 한다)`에서
`VITE_CHAIN_FROM_BLOCK`을 추가했지만, `docker/Dockerfile`의 `ARG` 목록에는
이 키가 선언되지 않았습니다.

- `.env`에 값이 있어도 Dockerfile에 대응 `ARG`가 없으면 Docker가 build-arg를
  "consumed 안 됨" 경고만 내고 조용히 버립니다. 빌드는 성공하지만 번들에는 빈 값이 들어가서
  `fromBlock`이 `0`으로 폴백 — 소재 등록 시 MetaMask 팝업이 뜨기까지 전체 체인을 다시 훑어
  느려지는 증상이 재현됐습니다.
- 이 어긋남을 잡는 "ARG 선언 여부 검사"는 `frontend/master`(운영) 빌드 스테이지에만 있었고,
  `/test` 스택용 `Test Deploy` 스테이지(`frontend/develop` 병합 시)에는 없어 조용히 넘어갔습니다.

## 무엇을 고쳤나

- `docker/Dockerfile` — `ARG VITE_CHAIN_FROM_BLOCK` 추가
- `Jenkinsfile` — `Test Deploy` 스테이지에도 master와 동일한 "ARG 선언 검사" 추가.
  이제 `.env`에 `VITE_*` 키가 있는데 Dockerfile에 `ARG` 선언이 없으면 test 빌드도 즉시 실패합니다.
- `README.md` — `VITE_*` 키 추가 시 Dockerfile `ARG` 선언을 함께 해야 한다는 규칙 명시

## 앞으로 새 VITE_* 키를 추가할 때

`.env.example`에 키를 추가하면 **같은 커밋에서 `docker/Dockerfile`의 `ARG` 목록에도 반드시
같은 이름을 추가하세요.** 이제 빠뜨리면 `frontend/develop` 병합 시 Test Deploy 빌드가 즉시
실패로 알려줍니다(예전에는 조용히 느려지기만 했습니다).
