import { id } from 'ethers'
import { getReadContract } from '../../../services/blockchain/contractFactory.ts'
import { getWalletAddress } from '../../wallet/services/walletService.ts'

const BRAND_ROLE = id('BRAND_ROLE')
const PROCESSOR_ROLE = id('PROCESSOR_ROLE')

type RegistrationPreparation = {
  brandWallet: string
  processorWallet: string
  inputTokenId: number
  inputQuantity: number
}

/**
 * 가공 작업을 체인에 등록하기 <b>전에</b> 되돌려질 이유가 있는지 체인을 읽어 본다.
 *
 * <h2>왜 필요한가</h2>
 *
 * 등록(`createJob`)이 "간헐적으로" 실패하던 이유는 모두 서명 창을 열기 전에 알 수 있는 것이었다.
 * MetaMask 에 가공업체 지갑이 선택돼 있어 `AccessControlUnauthorizedAccount(BRAND_ROLE)` 로
 * 되돌려진 일이 실제로 있었고, 체인과 DB 의 잠금이 어긋나 `insufficient material` 이 난 일도
 * 있었다. 그대로 보내면 ethers 가 estimateGas 원문을 던지고, 사용자는 무엇을 해야 할지 모른다.
 *
 * <h2>읽기 실패로는 막지 않는다</h2>
 *
 * RPC 가 잠시 응답하지 않아도 등록은 시도할 수 있어야 한다. 조건이 정말 틀렸다면 컨트랙트가
 * 되돌린다 — 막는 것은 <b>틀렸다는 확실한 증거</b>가 있을 때뿐이다.
 */
export async function assertCanRegisterProcessingJob(preparation: RegistrationPreparation): Promise<void> {
  const signer = await getWalletAddress()

  // 지갑이 아직 연결되지 않았으면 서명 단계가 연결부터 묻는다. 누가 서명할지 모르니 지갑·잔량은 볼 수 없다.
  if (signer && !sameAddress(signer, preparation.brandWallet)) {
    throw new Error(
      `MetaMask 에서 이 브랜드의 지갑(${short(preparation.brandWallet)})을 선택한 뒤 다시 시도해 주세요. ` +
      `지금 선택된 지갑은 ${short(signer)} 입니다.`,
    )
  }

  const registry = getReadContract('processingRegistry')
  if (signer && (await read(() => registry.hasRole(BRAND_ROLE, signer) as Promise<boolean>)) === false) {
    throw new Error('이 지갑에는 블록체인에서 가공을 맡길 브랜드 권한이 없습니다. 관리자에게 권한 부여를 요청해 주세요.')
  }
  if ((await read(() => registry.hasRole(PROCESSOR_ROLE, preparation.processorWallet) as Promise<boolean>)) === false) {
    throw new Error('가공업체 지갑에 블록체인 가공 권한이 없어 등록할 수 없습니다. 관리자에게 가공업체 권한 부여를 요청해 주세요.')
  }

  if (!signer) return
  const available = await read(() =>
    getReadContract('materialToken').availableBalance(signer, preparation.inputTokenId) as Promise<bigint>)
  if (available !== null && available < BigInt(preparation.inputQuantity)) {
    throw new Error(
      `블록체인에 남은 이 소재의 가용 수량(${grams(available)})이 가공 수량(${grams(BigInt(preparation.inputQuantity))})보다 적습니다. ` +
      '판매·생산에 잠긴 수량이나 아직 체인에 반영되지 않은 거래가 있는지 확인해 주세요.',
    )
  }
}

async function read<T>(call: () => Promise<T>): Promise<T | null> {
  try {
    return await call()
  } catch {
    return null
  }
}

function sameAddress(a: string, b: string): boolean {
  return a.toLowerCase() === b.toLowerCase()
}

function short(address: string): string {
  return `${address.slice(0, 6)}…${address.slice(-4)}`
}

function grams(value: bigint): string {
  return `${value.toLocaleString('ko-KR')} g`
}
