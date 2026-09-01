# 📢 Besu 연동 — 수령 완료 항목과 남은 요청 사항 (2026-09-01)

## 요약

SSAFY 제공 외부 Besu 노드를 쓰기로 확정됐습니다. **인프라 쪽 추가 작업은 거의 없습니다.**

| 항목 | 값 |
| --- | --- |
| RPC 엔드포인트 | `https://rpc.ssafy-blockchain.com` (HTTPS, 443) |
| Chain ID | `31221` (= `0x79f5`) |
| 노드 위치 | SSAFY 외부 서버 — 우리 EC2 아님 |

`.env.example` 과 `docs/INFRA_SETUP.md` 에 반영했고, 연결 검증용 `scripts/check-besu.sh` 를 추가했습니다.

### 실제로 찔러본 결과 (2026-09-01, 개발 PC 기준)

| 검사 | 결과 |
| --- | --- |
| `eth_chainId` | `0x79f5` = **31221** — 공유받은 값과 일치 ✅ |
| `eth_blockNumber` | `0xaa53b8` (약 11,162,552) — 노드 정상 가동 중 |
| `admin_peers` | 차단됨 ✅ (ADMIN namespace 노출 없음) |
| IP allowlist | **없음** — 아무 IP 에서나 응답. 미확인 항목이었는데 해소됐습니다 |

`ADMIN` 이 막혀 있는 것과 allowlist 가 없는 것이 확인됐으니 C-1·C-2 는 사실상 정리됐습니다.
다만 `QBFT` / `PERM` / `DEBUG` 계열은 개별 메서드를 알아야 찔러볼 수 있어 확인이 남아 있습니다.

**아직 남은 검증:** 위는 개발 PC 에서 물어본 결과입니다. 실제 호출 주체는 EC2 의 backend
컨테이너이므로, EC2 에서 `bash scripts/check-besu.sh` 를 한 번 돌려 컨테이너 경로(내부 DNS,
컨테이너 outbound)까지 확인해야 완결됩니다.

## 왜 인프라 작업이 없는가

원본이 **HTTPS** 라는 점이 크게 갈랐습니다. 평문 `http://...:8545` 였다면 우리 페이지가 https 라
브라우저가 mixed content 로 **차단**하기 때문에 nginx 에 `/rpc/` 프록시를 새로 만들어야 했습니다.

| 우려했던 것 | 결론 |
| --- | --- |
| Mixed content 차단 | 없음 — 원본이 https |
| CORS | 없음 — MetaMask 확장이 호출하므로 페이지 컨텍스트가 아니다 |
| nginx `/rpc/` 프록시 | 불필요 |
| UFW inbound 8545 | 불필요 — 우리가 노드를 띄우지 않는다 |
| EC2 → 외부 443 outbound | UFW outgoing 기본 allow — 작업 없음 |
| compose 에 besu 컨테이너 | 불필요 — 서버 리소스 부담 0 |

특히 마지막 항목이 반가운 부분입니다. EC2 1대에 MySQL + JVM + Jenkins + Python AI + nginx 2개가
이미 올라가 있어서, 노드를 우리 서버에서 돌렸다면 메모리가 빡빡했습니다.

nginx 에 외부 노드를 `upstream` 으로 걸지 않은 것도 의도적입니다. `upstream` 은 nginx **기동 시점에**
호스트명을 해석하고 실패하면 nginx 자체가 뜨지 않습니다 — 즉 SSAFY 노드가 죽으면 우리 서비스
전체가 내려갑니다. 프록시가 필요 없으므로 이 위험 자체가 없습니다.

## 1번(Blockchain) 담당에게 남은 요청

### 🔴 A. 지금 필요 — 이게 없으면 서버 서명 트랜잭션이 안 됩니다

**A-1. `BLOCKCHAIN_SERVICE_PRIVATE_KEY`**

백엔드 서버 서명 전용 지갑의 private key. 사용자 지갑(MetaMask)과 별개인 계정입니다.

전달 방법: **GitLab issue / MR / 채팅에 붙이지 말아주세요.** 1:1 DM 또는 서버 접속 후 직접 입력이
필요합니다. EC2 `.env`(퍼미션 600, `.gitignore` 처리됨)에만 저장하고, 컨테이너에는 백엔드에만
전달됩니다. `VITE_` 계열로는 절대 나가지 않습니다 — `VITE_*` 는 빌드 시점에 JS 번들로 인라인되어
브라우저에 평문 노출됩니다.

함께 확인이 필요합니다: **그 계정에 가스비로 쓸 잔액이 있는지.** 프라이빗 체인이라 가스가 0일
수도 있지만, 아니라면 잔액 없는 계정으로는 `eth_sendRawTransaction` 이 실패합니다.

### 🟡 B. 실배포 후 필요 — 늦어질수록 통합 리스크가 쌓입니다

**B-1. 컨트랙트 주소 3종**

`MaterialToken` / `PaymentToken` / `Escrow`(또는 `Marketplace`) — 이름이 확정 전이라고 하셨는데,
**주소보다 이름 확정이 먼저 필요합니다.** 프론트·백엔드가 각자 읽을 환경변수 키를 미리 정해두면
주소만 나중에 채우면 되기 때문입니다.

**B-2. ABI 파일**

이건 환경변수가 아니라 **JSON 파일**이라서 `.env` 로 전달할 수 없습니다. 경로 합의가 필요합니다:

- 프론트/백엔드 각 레포에 커밋? (누가 갱신하는가)
- 1번 브랜치에서 빌드 시점에 가져오기?
- 재배포 시 ABI 버전 갱신을 누가 알리는가?

정해두지 않으면 데모 직전에 "주소는 맞는데 함수 시그니처가 안 맞는" 상황이 생깁니다.

**B-3. 주소 확정(freeze) 시점**

컨트랙트를 재배포하면 주소가 바뀌고, `VITE_*_ADDRESS` 는 빌드 시점 값이라 **프론트 재빌드 +
재배포가 필요합니다.** 데모 전날 재배포되면 그날 배포 작업이 한 번 더 생깁니다.
"이 날짜 이후로는 재배포하지 않는다" 를 미리 합의해두면 좋겠습니다.

### 🟢 C. 확인만 부탁 — 우리가 고칠 수는 없는 항목

**C-1. RPC namespace 노출 범위**

필요한 `ETH` 계열 메서드 목록은 공유받았습니다. 확인이 필요한 건 **불필요하게 열려 있는 것이
없는지** 입니다 — `ADMIN` / `QBFT` / `PERM` / `DEBUG` / `PERSONAL` 계열.

공개 엔드포인트라 누구나 호출할 수 있으므로, 열려 있으면 노드 조작이 가능합니다. SSAFY 관리
노드라 우리가 손댈 수 없으니 **확인해서 알리는 것까지가 우리 역할**입니다.
`scripts/check-besu.sh` 가 `admin_peers` 를 찔러보고 응답하면 경고를 출력합니다.

**C-2. IP allowlist** — ✅ 해소됨

개발 PC 에서 응답이 왔으므로 allowlist 는 걸려 있지 않습니다. 별도 요청 불필요합니다.
(EC2 에서의 재확인만 남았습니다 — 위 "실제로 찔러본 결과" 참고)

## 프론트엔드 담당에게 확인 요청

`VITE_*` 는 Jenkins frontend 파이프라인이 `--build-arg` 로 주입해야 번들에 들어갑니다.
`deploy/master` 에는 Jenkinsfile 이 없고 **파트 브랜치가 소유**하므로, 프론트 Jenkinsfile 에
`.env` 의 `VITE_*` 를 읽어 `--build-arg` 로 넘기는 로직이 실제로 있는지 확인이 필요합니다.

없으면 `.env` 에 값을 채워도 **번들에 반영되지 않습니다.** 지금은 값이 비어 있어 증상이 같아서
구분이 안 되니, RPC URL·Chain ID 가 채워진 지금 시점에 한 번 확인해두면 좋습니다.

## 백엔드 담당에게 확인 요청

현재 `docker-compose.yml` 의 backend 는 `BESU_RPC_URL` / `BESU_CHAIN_ID` /
`BLOCKCHAIN_SERVICE_PRIVATE_KEY` **세 개만** 전달합니다. Web3j 로 컨트랙트를 호출하려면
**컨트랙트 주소도 서버에 필요한데 주입 경로가 없습니다.**

읽을 키 이름을 알려주시면 compose 에 추가하겠습니다 (`.env.example` 에는
`MATERIAL_TOKEN_ADDRESS` / `PAYMENT_TOKEN_ADDRESS` / `ESCROW_ADDRESS` 로 자리만 잡아뒀습니다).

Spring 은 OS 환경변수가 `application.yml` 보다 우선합니다. compose 가 넘기지 않는 키를
`application.yml` 에서 `${...}` 로 읽고 있고 기본값이 없다면 **기동 자체가 실패**하니,
키 이름은 양쪽이 정확히 맞아야 합니다.

## 당장 막혀 있지는 않습니다

`docs/DEVELOPMENT_STRATEGY.md` 8-2 의 `MockBlockchainService` 구조가 살아 있고, compose 도
`${BESU_RPC_URL:-}` 로 빈 값 기본값을 줘서 값 없이 기동됩니다. 3·4·5번은 1번을 기다리지 않고
개발을 계속할 수 있습니다.

다만 **A-1(private key)** 은 서버 서명 경로를 한 번이라도 실제로 돌려보려면 필요합니다.
Mock 으로 끝까지 가면 통합 시점에 문제가 몰립니다.
