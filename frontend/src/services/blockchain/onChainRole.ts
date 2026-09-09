import { id as keccakUtf8 } from 'ethers'
import { getReadContract } from './contractFactory.ts'
import { getSigner } from '../../features/wallet/services/walletService.ts'
import { createAppError, ERROR_KIND } from '../../utils/errors.ts'

/**
 * 컨트랙트가 요구하는 온체인 역할.
 *
 * <b>앱의 역할(JWT 의 role)과 다른 것이다.</b> 앱의 역할은 회원가입으로 정해지지만
 * 온체인 역할은 <b>지갑 주소마다</b> 관리자가 따로 부여한다. 그래서 BRAND 로 가입한
 * 사용자의 지갑에 {@code BRAND_ROLE} 이 없을 수 있고, 그 상태로는 구매 서명이 되돌려진다.
 *
 * 세 역할 모두 {@code MaterialToken} 이 들고 있다 — {@code Marketplace} 도 자기 것을
 * 두지 않고 {@code materialToken.hasRole(...)} 로 되묻는다(Marketplace.sol:139, 208).
 */
export type OnChainRole = 'SUPPLIER_ROLE' | 'BRAND_ROLE' | 'PROCESSOR_ROLE'

/** 무엇을 하려다 막혔는지 사용자에게 말해 주기 위한 이름. */
const ROLE_LABEL: Record<OnChainRole, string> = {
  SUPPLIER_ROLE: '공급자',
  BRAND_ROLE: '브랜드',
  PROCESSOR_ROLE: '가공업체',
}

/**
 * 역할 해시. Solidity 의 {@code keccak256("SUPPLIER_ROLE")} 과 같은 값이다 —
 * ethers 의 {@code id()} 가 문자열의 UTF-8 바이트를 그대로 keccak256 한다.
 *
 * 컨트랙트에서 읽어 올 수도 있지만({@code materialToken.SUPPLIER_ROLE()}) 그러면
 * 확인 한 번에 RPC 왕복이 두 번이 된다. 값이 상수이므로 여기서 계산하고, 배포된
 * 컨트랙트와 같은지는 테스트가 고정한다.
 */
function roleHash(role: OnChainRole): string {
  return keccakUtf8(role)
}

/**
 * 서명 전에 이 지갑이 그 역할을 가졌는지 확인한다.
 *
 * <b>왜 미리 보는가</b> — 없으면 트랜잭션이 {@code Marketplace: buyer role required}
 * 같은 영문 revert 로 되돌려진다. 사용자는 자기가 무엇을 잘못했는지 알 수 없고, 사실
 * 잘못한 것도 없다 — 관리자가 그 지갑에 역할을 부여해야 하는 일이다. 읽기 호출이라
 * 가스가 들지 않으므로 미리 물어보고 사람이 읽을 수 있는 말로 알려 준다.
 *
 * <b>확인할 수 없으면 막지 않는다.</b> RPC 가 실패했다고 서명을 못 하게 하면, 정작
 * 역할이 있는 사용자가 우리 쪽 사정으로 막힌다. 그때는 컨트랙트의 판단에 맡긴다 —
 * 그쪽이 정본이고, 최악이라도 지금과 같은 revert 다.
 */
export async function requireOnChainRole(role: OnChainRole): Promise<void> {
  // getWriteContract 가 이미 계정 승인을 마친 뒤에 불리므로 팝업이 새로 뜨지 않는다.
  const address = await (await getSigner()).getAddress()

  let granted: boolean
  try {
    const materialToken = getReadContract('materialToken')
    granted = (await materialToken.hasRole(roleHash(role), address)) as boolean
  } catch {
    return
  }

  if (!granted) {
    throw createAppError(ERROR_KIND.TRANSACTION_FAILED, {
      message:
        `이 지갑(${shortAddress(address)})에 온체인 ${ROLE_LABEL[role]} 권한이 없습니다. ` +
        '회원 유형과 별개로 관리자가 지갑 주소마다 부여하는 권한입니다. ' +
        '팀의 컨트랙트 담당자에게 이 주소의 권한 부여를 요청해 주세요.',
    })
  }
}

function shortAddress(value: string): string {
  return `${value.slice(0, 8)}…${value.slice(-6)}`
}
