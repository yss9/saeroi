# Besu 연동 — Frontend / Backend 담당자 인수 문서

> **읽는 시점**: 이 문서는 담당자가 블록체인 연동 작업에 **투입되기 전에** 작성됐습니다.
> 해당 작업을 시작하는 사람이 처음 읽는 문서라고 가정하고, 사전 지식 없이 읽을 수 있게 썼습니다.
>
> 작성: Infra(6번), 2026-09-01 · 관련: `TEAM_NOTICE_2026-09-01-besu.md`, `docs/INFRA_SETUP.md`

---

## 0. 먼저 — 확정된 사실

| 항목 | 값 | 상태 |
| --- | --- | --- |
| Besu RPC | `https://rpc.ssafy-blockchain.com` | ✅ 확정, 실측 검증됨 |
| Chain ID | `31221` (= `0x79f5`) | ✅ 확정, `eth_chainId` 로 일치 확인 |
| 노드 위치 | SSAFY 제공 외부 서버 (우리 EC2 아님) | ✅ |
| IP allowlist | 없음 — 아무 곳에서나 호출 가능 | ✅ 확인됨 |
| `ADMIN` namespace | 차단됨 | ✅ 확인됨 |
| 컨트랙트 주소 | **미정** — 1번의 SSAFY 실배포 전 | ⏳ 대기 |
| ABI 파일 | **전달 경로 미합의** | ⏳ 대기 |
| `BLOCKCHAIN_SERVICE_PRIVATE_KEY` | 수령·EC2 반영 완료 (0x 없는 16진수 64자) | ✅ 2026-09-01 |
| 가스 | **0 — 프리가스 네트워크** (`eth_gasPrice` `0x0`) | ✅ 확인됨 |
| 서명 계정 컨트랙트 role | **미확인** — 주소·role 미수령. 없으면 트랜잭션이 리버트된다 | ⏳ 대기 |

**HTTPS 엔드포인트라는 점이 중요합니다.** 우리 서비스가 `https://j15e104.p.ssafy.io` 이므로,
RPC 가 평문 `http://` 였다면 브라우저가 mixed content 로 **차단**해서 nginx 에 `/rpc/` 프록시를
만들어야 했습니다. 원본이 https 라서 프론트에서 URL 을 그대로 쓰면 됩니다. 프록시 없습니다.

---

## 1. Frontend 담당자에게

### 1-1. 🔴 가장 먼저 확인할 것 — build-arg 주입 로직

**결론부터: 프론트 Jenkinsfile 이 `.env` 의 `VITE_*` 를 `--build-arg` 로 넘기지 않으면,
EC2 `.env` 에 값을 채워도 번들에 들어가지 않습니다.**

왜 이게 조용히 깨지는가:

- `VITE_*` 는 런타임 환경변수가 **아닙니다**. Vite 가 **빌드 시점에** 값을 소스에 문자열로
  치환해 넣습니다. 번들이 만들어진 뒤에는 바꿀 방법이 없습니다.
- 그래서 `docker compose up` 으로는 절대 반영되지 않습니다. **이미지 재빌드**가 필요합니다.
- `deploy/master` 에는 Jenkinsfile 이 없습니다. Jenkinsfile 은 **각 파트 브랜치가 소유**합니다
  (`docs/INFRA_SETUP.md` 파이프라인 소유권 표 참고). 즉 이 주입은 프론트 파트의 책임입니다.
- 지금까지 값이 비어 있었기 때문에 **"주입 로직이 없는 것"과 "값이 비어 있는 것"의 증상이
  똑같았습니다.** 구분이 안 됐습니다. 이제 RPC URL·Chain ID 가 채워졌으니 지금이 확인 적기입니다.

확인 방법 — 프론트 파이프라인을 한 번 돌린 뒤 번들에 값이 박혔는지 직접 봅니다:

```bash
# EC2 에서. 번들 안에 chain ID 가 실제로 들어갔는지
docker exec cf-frontend sh -c 'grep -ro "31221" /usr/share/nginx/html/assets | head'
```

아무것도 안 나오면 주입이 안 되고 있습니다.

Jenkinsfile 에 필요한 형태(참고용 — 실제 구조는 파트 사정에 맞게):

```groovy
// .env 는 EC2 호스트의 /home/ubuntu/deploy/.env 에 있고,
// Jenkins 컨테이너에 같은 절대경로로 :ro 마운트되어 있다 (docker-compose.yml 참고)
sh '''
  set -a; . /home/ubuntu/deploy/.env; set +a
  docker build \
    --build-arg VITE_API_BASE_URL="$VITE_API_BASE_URL" \
    --build-arg VITE_BESU_RPC_URL="$VITE_BESU_RPC_URL" \
    --build-arg VITE_BESU_CHAIN_ID="$VITE_BESU_CHAIN_ID" \
    --build-arg VITE_MATERIAL_TOKEN_ADDRESS="$VITE_MATERIAL_TOKEN_ADDRESS" \
    --build-arg VITE_PAYMENT_TOKEN_ADDRESS="$VITE_PAYMENT_TOKEN_ADDRESS" \
    --build-arg VITE_ESCROW_ADDRESS="$VITE_ESCROW_ADDRESS" \
    -t circular-fashion/frontend:latest .
'''
```

Dockerfile 쪽에도 `ARG` → `ENV` 선언이 있어야 Vite 가 읽습니다. `ARG` 만 있으면 빌드 스테이지의
셸 변수일 뿐이라 `import.meta.env` 로 들어가지 않습니다:

```dockerfile
ARG VITE_BESU_CHAIN_ID
ENV VITE_BESU_CHAIN_ID=$VITE_BESU_CHAIN_ID
```

### 1-2. 지금 쓸 수 있는 값

```
VITE_BESU_RPC_URL=https://rpc.ssafy-blockchain.com
VITE_BESU_CHAIN_ID=31221
```

`MR !60 (frontend/feat/wallet-connect)` 의 체인 검증 UI 가 `VITE_BESU_CHAIN_ID` 가 채워질 때만
켜지도록 되어 있습니다. 이제 켤 수 있습니다.

### 1-3. 컨트랙트 주소 — 아직 비워두세요

이름이 확정 전입니다. 현재 `.env.example` 기준:

```
VITE_MATERIAL_TOKEN_ADDRESS=
VITE_PAYMENT_TOKEN_ADDRESS=
VITE_ESCROW_ADDRESS=
```

주소가 나오면 **재빌드 + 재배포**가 필요합니다(1-1 이유). 데모 직전에 컨트랙트가 재배포되면
그날 배포가 한 번 더 생기므로, 1번과 freeze 시점을 합의해둔 상태입니다.

### 1-4. 보안 — `VITE_` 에 넣으면 안 되는 것

`VITE_*` 는 **빌드 시점에 JS 번들로 인라인되어 브라우저에 평문 노출됩니다.** 누구나 개발자 도구로
읽을 수 있습니다.

- ❌ `VITE_BLOCKCHAIN_SERVICE_PRIVATE_KEY` 같은 건 절대 만들지 마세요
- ❌ API 키, DB 비밀번호도 동일
- ✅ 비밀값이 필요한 동작은 backend 를 경유합니다

사용자 서명은 MetaMask 가 사용자 자신의 키로 합니다. 서버 키가 프론트로 내려갈 이유가 없습니다.

### 1-5. CORS / mixed content

**신경 쓸 것 없습니다.** RPC 가 https 이고, 사용자 서명 트랜잭션은 MetaMask 확장이 호출하므로
페이지 컨텍스트가 아닙니다.

다만 **페이지 JS 가 직접 RPC 를 호출한다면**(예: 읽기 전용 조회를 `ethers.JsonRpcProvider` 로)
그때는 CORS 가 걸릴 수 있습니다. SSAFY 노드의 `--rpc-http-cors-origins` 설정에 우리 도메인이
있어야 합니다. 이 경로가 필요해지면 Infra 에 알려주세요 — 노드 설정 요청이나 nginx `/rpc/`
프록시 추가로 해결합니다(현재는 불필요해서 만들지 않았습니다).

읽기 조회를 backend 경유로 하면 이 문제 자체가 없습니다.

---

## 2. Backend 담당자에게

### 2-1. ✅ 해결됨 (2026-09-01) — 컨트랙트 주소 주입 경로 추가

이전에는 `docker-compose.yml` 의 backend 가 받는 블록체인 환경변수가 **3개뿐**이라
Web3j 로 컨트랙트를 호출하는 데 필요한 **주소가 서버로 넘어가지 않았습니다.**

이름이 확정되어 compose 에 추가했습니다. 아래 이름 그대로 읽으시면 됩니다:

```yaml
BESU_RPC_URL: ${BESU_RPC_URL:-}
BESU_CHAIN_ID: ${BESU_CHAIN_ID:-}
BLOCKCHAIN_SERVICE_PRIVATE_KEY: ${BLOCKCHAIN_SERVICE_PRIVATE_KEY:-}
MATERIAL_TOKEN_ADDRESS: ${MATERIAL_TOKEN_ADDRESS:-}
PAYMENT_TOKEN_ADDRESS: ${PAYMENT_TOKEN_ADDRESS:-}
ESCROW_ADDRESS: ${ESCROW_ADDRESS:-}
```

> ⚠️ **`application.yml` 에서 읽을 때 반드시 기본값을 두세요.**
> OS 환경변수가 `application.yml` 보다 우선합니다. `${MATERIAL_TOKEN_ADDRESS}` 를
> **기본값 없이** 쓰면, 그 키가 빠지는 순간 **기동 자체가 실패**합니다.
> compose 쪽에 `:-` 를 붙여 항상 정의되게 해뒀지만, yml 쪽에도
> `${MATERIAL_TOKEN_ADDRESS:}` 처럼 방어해 두시는 편이 안전합니다.
> (같은 함정이 `DB_USERNAME`/`DB_PASSWORD` 에 아직 남아 있습니다.)

**값은 아직 비어 있습니다** — 1번의 실배포 전이라 주소가 미정입니다. 주소가 채워지면
backend 는 컨테이너 재생성으로 반영됩니다 (`restart` 로는 안 됩니다).

### 2-2. ⚠️ Spring 환경변수 우선순위 — 기동이 깨지는 지점

**OS 환경변수가 `application.yml` 보다 우선합니다.** 이게 지금 DB 설정이 동작하는 이유이기도
하고, 동시에 함정입니다.

`docker-compose.yml` 의 backend 주석에 이미 기록된 사례:

> `application.yml` 은 `${DB_URL}`/`${DB_USERNAME}`/`${DB_PASSWORD}` 를 읽는데, 지금 동작하는
> 이유는 OS 환경변수가 우선이라 그 placeholder 가 **아예 평가되지 않기** 때문이지 설계된 동작이
> 아니다. `DB_USERNAME`/`DB_PASSWORD` 에는 기본값도 없어서, yml 쪽이 한 번이라도 우선하게 되는
> 순간 기동이 깨진다.

컨트랙트 주소도 같은 함정에 빠질 수 있습니다:

- `application.yml` 에서 `${MATERIAL_TOKEN_ADDRESS}` 를 읽고 **기본값이 없으면**,
  compose 가 그 키를 넘기지 않는 순간 **기동 자체가 실패**합니다
- 안전한 형태: `${MATERIAL_TOKEN_ADDRESS:}` (빈 기본값) 로 두고, 값이 없을 때는
  Mock 구현으로 떨어지게 하는 것

키 이름은 compose 와 `application.yml` 양쪽이 **정확히** 일치해야 합니다.

### 2-3. `BLOCKCHAIN_SERVICE_PRIVATE_KEY`

- 사용자 지갑(MetaMask)과 **별개**인 서버 전용 지갑입니다
- EC2 `~/deploy/.env`(퍼미션 600, `.gitignore` 처리)에만 저장되고, compose 가 backend
  컨테이너에만 전달합니다
- **`.env` 를 `env_file:` 로 통째로 주입하지 않습니다.** 예전에 그렇게 했다가 backend 컨테이너에
  `MYSQL_ROOT_PASSWORD` / `JENKINS_ADMIN_PASSWORD` / `GITLAB_TOKEN` 까지 들어가 있었습니다.
  지금은 필요한 키만 명시 전달합니다 — 그래서 **새 키가 필요하면 compose 수정이 필요합니다**
  (Infra 에 요청해주세요)
- **2026-09-01 수령·반영 완료.** 형식은 **`0x` 없는 16진수 64자**입니다.
  Web3j `Credentials.create()` 는 `0x` 유무를 모두 받지만 라이브러리마다 다를 수 있어 명시해 둡니다.
  반영 후 컨테이너 안 길이 64 확인, 기동 로그에 키가 새지 않는 것(64자 hex 패턴 0건)도 확인했습니다
- ✅ **가스 잔액은 신경 쓰지 않아도 됩니다 — 프리가스 네트워크입니다** (2026-09-01 실측)

  ```
  eth_gasPrice   : 0x0                  ← 가스 가격 0
  baseFeePerGas  : 필드 자체가 없음        ← EIP-1559 미적용
  gasLimit       : 0x1fffffffffffff     ← 사실상 무제한
  ```

  **잔액 0 인 계정으로도 트랜잭션이 나갑니다.** `PaymentToken.sol` 주석과도 맞습니다 —
  네이티브 코인을 안 주는 대신 가스를 안 받는 구성입니다. 그래서 결제용 ERC-20 을 따로 발행합니다.

  `scripts/check-besu.sh` 가 매번 `eth_gasPrice` 를 확인하므로, SSAFY 가 나중에 유료로
  바꾸면 그때 드러납니다.

- 🔴 **대신 진짜 관문은 컨트랙트 role 입니다.**

  `MaterialToken` 은 `AccessControl` 기반입니다:

  | 함수 | 필요한 role |
  | --- | --- |
  | `mintMaterial()` | `SUPPLIER_ROLE` |
  | `verifyMaterial()` | `VERIFIER_ROLE` |
  | `setMaterialStatus()` | `STATUS_MANAGER_ROLE` |
  | `burnMaterial()` | 소유자 또는 approved (role 아님) |

  생성자는 **admin 에게만** 전 권한을 줍니다. **서버 서명 계정이 admin 이 아니면 가스가
  공짜여도 `AccessControlUnauthorizedAccount` 로 리버트됩니다.**

  1번에게 확인할 것: 서명 계정 주소 / 그 계정이 가진 role / 없다면 `grantRole` 요청.
  주소와 컨트랙트 주소를 받으면 조회할 수 있습니다:

  ```bash
  # hasRole(SUPPLIER_ROLE, <서명계정>) 조회 예시
  docker exec cf-backend sh -c 'curl -s -X POST https://rpc.ssafy-blockchain.com \
    -H "Content-Type: application/json" \
    -d "{\"jsonrpc\":\"2.0\",\"method\":\"eth_call\",\"params\":[{\"to\":\"<컨트랙트주소>\",\"data\":\"<hasRole 인코딩>\"},\"latest\"],\"id\":1}"'
  ```

### 2-4. RPC 연결 검증

값이 반영된 뒤 연결이 되는지는 스크립트로 확인합니다:

```bash
bash scripts/check-besu.sh
```

`cf-backend` 컨테이너 **안에서** RPC 를 물어봅니다. 호스트에서의 성공은 컨테이너 내부 DNS·
outbound 를 증명하지 않기 때문입니다. `eth_chainId` 응답(hex)을 10진으로 변환해
`BESU_CHAIN_ID` 와 비교하고, `admin_peers` 노출도 함께 봅니다.

수동으로 확인할 때:

```bash
docker exec cf-backend sh -c 'curl -s -X POST https://rpc.ssafy-blockchain.com \
  -H "Content-Type: application/json" \
  -d "{\"jsonrpc\":\"2.0\",\"method\":\"eth_chainId\",\"params\":[],\"id\":1}"'
```

### 2-5. 사용 가능한 RPC 메서드

1번이 확인해준 목록:

```
eth_chainId          eth_blockNumber      eth_getBalance
eth_call             eth_sendRawTransaction
eth_getTransactionReceipt
eth_getCode          eth_estimateGas
```

`ADMIN` 은 차단 확인됐습니다. `QBFT`/`PERM`/`DEBUG` 는 확인 대기 중이지만 애플리케이션이 쓸
메서드는 아닙니다.

### 2-6. 스키마 전략 — 별개지만 선결 조건

블록체인과 무관하지만 함께 짚어둘 사항입니다. 현재 backend 는 `ddl-auto: validate` 이고
Flyway 가 없습니다. `@Entity` 가 0개라 지금은 통과하지만, **엔티티를 추가하는 순간 기동이
깨집니다.** `BlockchainTransaction` 같은 엔티티를 만들 때 Flyway 도입 여부를 함께
결정해야 합니다.

---

## 3. 아직 개발이 막혀 있지는 않습니다

`docs/DEVELOPMENT_STRATEGY.md` 8-2 에 `MockBlockchainService` 전략이 있습니다:

| 시기 | 구현 | 동작 |
| --- | --- | --- |
| 초반 | `MockBlockchainService` | 가짜 Tx Hash 반환 |
| 이후 | `RealBlockchainService` | 실제 Contract / Web3j 연동 |

compose 도 `${BESU_RPC_URL:-}` 로 빈 값 기본값을 주므로 값 없이 기동됩니다.

**다만 Mock 으로만 끝까지 가면 통합 시점에 문제가 몰립니다.** private key 를 받는 즉시 서버 서명
경로를 한 번은 실제로 돌려보는 게 좋습니다.

---

## 4. 요약 — 담당자별 첫 액션

**Frontend**
1. Jenkinsfile 의 `VITE_*` → `--build-arg` 주입 로직 존재 여부 확인 (§1-1) ← **최우선**
2. Dockerfile 에 `ARG` + `ENV` 선언 확인
3. 파이프라인 1회 실행 후 번들에서 `31221` grep 으로 실증
4. 주소 3종은 1번 전달 후 재빌드

**Backend**
1. 컨트랙트 주소 환경변수 이름 확정 → Infra 에 전달 (§2-1) ← **최우선**
2. `application.yml` 의 `${...}` 에 빈 기본값 부여 (§2-2)
3. `bash scripts/check-besu.sh` 로 RPC 연결 확인
4. 엔티티 추가 전 Flyway 도입 여부 결정 (§2-6)

**Infra(6번)에 요청할 것**
- compose 에 환경변수 추가가 필요할 때 (`.env` 전체 주입을 하지 않으므로 새 키는 매번 추가 필요)
- 페이지 JS 가 RPC 를 직접 호출해야 해서 CORS 가 걸릴 때 (§1-5)
