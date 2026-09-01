#!/usr/bin/env bash
# 외부 SSAFY Besu 노드와의 연결을 검증한다.
#
# 사용법:
#   bash scripts/check-besu.sh          # .env 의 BESU_RPC_URL / BESU_CHAIN_ID 로 검사
#   BESU_RPC_URL=https://... bash scripts/check-besu.sh
#
# 반드시 backend 컨테이너 "안에서" 묻는다. 호스트에서만 성공하고 컨테이너에서 실패하는
# 경우가 있는데(내부 DNS, 컨테이너 outbound), 실제로 RPC 를 호출하는 주체는 백엔드이므로
# 호스트에서의 성공은 아무것도 증명하지 않는다.
#
# 판정:
#   - 응답이 오면 SSAFY 측 IP allowlist 가 없다는 뜻이다 (요청 시점의 미확인 항목이었다)
#   - 403 / timeout 이면 우리 EC2 공인 IP 등록을 1번 담당에게 요청해야 한다
#   - eth_chainId 는 hex 를 돌려준다. BESU_CHAIN_ID 와 비교할 때 10진 변환이 필요하다.
set -uo pipefail
DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DEPLOY_DIR"

# .env 를 통째로 source 하지 않는다. compose 의 .env 문법은 셸 문법이 아니어서
# (따옴표 없는 값에 공백이 있는 등) source 하면 엉뚱한 곳에서 깨진다. 게다가 이 스크립트에
# 필요 없는 비밀값까지 전부 환경에 올라온다. 필요한 두 키만 뽑아 쓴다.
envval() {
    [ -f .env ] || return 0
    sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*//p" .env | tail -n 1 | tr -d '"'"'"''
}

RPC="${BESU_RPC_URL:-$(envval BESU_RPC_URL)}"
EXPECTED_CHAIN_ID="${BESU_CHAIN_ID:-$(envval BESU_CHAIN_ID)}"
FAIL=0

if [ -z "$RPC" ]; then
    echo "FAIL: BESU_RPC_URL 이 비어 있습니다 (.env 확인)."
    exit 1
fi

# backend 컨테이너가 떠 있으면 그 안에서, 없으면 호스트에서 검사한다.
# 후자는 '컨테이너 경로 미검증' 이라고 분명히 알린다 — 조용히 약한 검사로 내려가지 않는다.
if docker inspect -f '{{.State.Running}}' cf-backend 2>/dev/null | grep -q true; then
    RUN() { docker exec cf-backend sh -c "$1"; }
    WHERE="cf-backend 컨테이너 안"
else
    RUN() { sh -c "$1"; }
    WHERE="호스트 (주의: cf-backend 가 떠 있지 않아 컨테이너 경로는 검증되지 않았다)"
fi

echo "── Besu RPC 연결 확인 ─────────────────────"
echo "대상   : $RPC"
echo "실행위치: $WHERE"
echo

# rpc <method> — JSON-RPC 한 번 호출하고 원문을 출력한다.
rpc() {
    local method="$1"
    RUN "curl -s --max-time 10 -X POST '$RPC' \
        -H 'Content-Type: application/json' \
        -d '{\"jsonrpc\":\"2.0\",\"method\":\"$method\",\"params\":[],\"id\":1}'"
}

RESP="$(rpc eth_chainId || true)"
if [ -z "$RESP" ]; then
    echo "FAIL: eth_chainId 응답이 없습니다 (timeout 또는 연결 거부)."
    echo "      → SSAFY 측 IP allowlist 에 우리 EC2 공인 IP 등록이 필요할 수 있습니다."
    exit 1
fi
echo "eth_chainId     : $RESP"

# {"result":"0x79f5"} 에서 hex 만 뽑는다.
HEX="$(printf '%s' "$RESP" | sed -n 's/.*"result"[[:space:]]*:[[:space:]]*"0x\([0-9a-fA-F]*\)".*/\1/p')"
if [ -z "$HEX" ]; then
    echo "FAIL: 응답에 result 가 없습니다 — 위 원문을 확인하세요 (권한 오류일 수 있습니다)."
    FAIL=1
else
    ACTUAL=$((16#$HEX))
    echo "  → 10진 변환    : $ACTUAL"
    if [ -n "$EXPECTED_CHAIN_ID" ] && [ "$ACTUAL" != "$EXPECTED_CHAIN_ID" ]; then
        echo "FAIL: BESU_CHAIN_ID($EXPECTED_CHAIN_ID) 와 노드 응답($ACTUAL) 이 다릅니다."
        FAIL=1
    fi
fi

echo "eth_blockNumber : $(rpc eth_blockNumber || echo '(응답 없음)')"

# 가스 가격. 2026-09-01 시점 이 네트워크는 0 이다 — 잔액 없는 계정으로도 트랜잭션이 나간다.
# PaymentToken.sol 주석("네이티브 코인을 지급받을 수 없으므로 결제 수단을 직접 발행한다")과
# 맞물리는 구성이다. SSAFY 가 나중에 유료로 바꾸면 서버 서명 계정에 잔액이 필요해지므로
# 여기서 매번 확인해 조용히 바뀌지 않게 한다.
GASRESP="$(rpc eth_gasPrice || true)"
GASHEX="$(printf '%s' "$GASRESP" | sed -n 's/.*"result"[[:space:]]*:[[:space:]]*"0x\([0-9a-fA-F]*\)".*/\1/p')"
if [ -z "$GASHEX" ]; then
    echo "eth_gasPrice    : (확인 불가) $GASRESP"
elif [ "$((16#$GASHEX))" -eq 0 ]; then
    echo "eth_gasPrice    : 0 — 프리가스 네트워크 (서명 계정 잔액 불필요)"
else
    echo "eth_gasPrice    : $((16#$GASHEX)) wei"
    echo "WARN: 가스가 더 이상 무료가 아닙니다. 서버 서명 계정에 잔액이 필요합니다."
    echo "      eth_getBalance 로 잔액을 확인하세요 (docs/notices/BESU_REQUESTS_FE_BE.md 참고)."
fi

# ADMIN namespace 노출 확인. 열려 있으면 노드를 조작당할 수 있으므로 반드시 막혀 있어야 한다.
# 여기서는 우리 노드가 아니라 SSAFY 노드라 우리가 고칠 수 없다 — 확인해서 알리는 것이 목적이다.
ADMIN="$(rpc admin_peers || true)"
case "$ADMIN" in
    *'"result"'*)
        echo
        echo "WARN: admin_peers 가 응답했습니다 — ADMIN namespace 가 공개되어 있습니다."
        echo "      1번 담당/SSAFY 에 알릴 사항입니다 (우리가 고칠 수 있는 부분은 아닙니다)."
        ;;
    *) echo "admin_peers     : 차단됨 (정상)" ;;
esac

echo
[ "$FAIL" = 0 ] && echo "결과: OK" || echo "결과: FAIL"
exit $FAIL
