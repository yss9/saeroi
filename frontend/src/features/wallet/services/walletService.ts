// ethers 를 사용하는 지갑 서비스 계층.
//
// 두 개의 서비스가 공존하는 이유를 먼저 밝혀둔다.
//
//   walletClient.ts  — EIP-1193 raw 계층. 연결 상태를 들고 있고 accountsChanged /
//                      chainChanged 를 구독한다. React 의 useWallet 이 이걸 구독한다.
//   walletService.ts — ethers 계층(여기). Provider·Signer·서명·잔액처럼 "그 순간에
//                      필요한 것을 즉석에서 만들어 쓰는" 일회성 작업을 담당한다.
//
// 상태는 walletClient 가, 능력(capability)은 walletService 가 갖는다.
//
// ⚠️ Provider 와 Signer 는 절대 모듈 전역이나 Zustand 에 캐시하지 않는다.
// 사용자가 계정이나 체인을 바꾸면 기존 객체는 낡은 대상을 가리키게 되고,
// 그 상태로 서명하면 엉뚱한 계정으로 트랜잭션이 나간다. 매번 새로 만든다.

import { BrowserProvider, type JsonRpcSigner } from 'ethers'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import { RPC_ERROR, type EthereumProvider } from '../types/wallet.types.ts'

declare global {
  interface Window {
    ethereum?: EthereumProvider
  }
}

/**
 * 주입된 EIP-1193 provider 를 가져온다.
 *
 * 지갑 확장을 여러 개 설치한 사용자는 `window.ethereum` 을 놓고 경합한다.
 * 그 경우를 제대로 풀려면 EIP-6963(다중 provider 탐색)이 필요한데 지금은 과잉이다.
 * 나중에 바꾸더라도 이 함수 하나만 교체하면 되도록 분리해 둔다.
 */
export function getInjectedProvider(): EthereumProvider | null {
  return typeof window === 'undefined' ? null : (window.ethereum ?? null)
}

/** MetaMask(혹은 호환 지갑)가 설치되어 있는가. */
export function isWalletAvailable(): boolean {
  return getInjectedProvider() !== null
}

/**
 * ethers BrowserProvider 를 만든다. 지갑이 없으면 null.
 * 읽기 전용 호출에도 쓸 수 있어 연결 여부를 따지지 않는다.
 */
export function getBrowserProvider(): BrowserProvider | null {
  const injected = getInjectedProvider()
  return injected ? new BrowserProvider(injected) : null
}

/**
 * 서명 가능한 Signer 를 얻는다.
 *
 * 계정이 연결되어 있지 않으면 ethers 가 내부적으로 `eth_requestAccounts` 를 호출해
 * MetaMask 팝업을 띄운다. 즉 이 함수는 사용자 상호작용을 유발할 수 있으므로
 * 렌더링 중이 아니라 이벤트 핸들러 안에서 부른다.
 */
export async function getSigner(): Promise<JsonRpcSigner> {
  const provider = getBrowserProvider()
  if (!provider) throw createAppError(ERROR_KIND.WALLET_NOT_FOUND)
  try {
    return await provider.getSigner()
  } catch (error) {
    throw toWalletError(error)
  }
}

/** 현재 연결된 주소 (EIP-55 체크섬). 연결되어 있지 않으면 null. */
export async function getWalletAddress(): Promise<string | null> {
  const provider = getBrowserProvider()
  if (!provider) return null
  try {
    // listAccounts 는 팝업을 띄우지 않는다 — 이미 승인된 계정만 돌려준다.
    const accounts = await provider.listAccounts()
    return accounts.length > 0 ? accounts[0].address : null
  } catch {
    return null
  }
}

/** 현재 체인 ID 를 16진수 문자열로 돌려준다 (예: '0x539'). 실패 시 null. */
export async function getChainId(): Promise<string | null> {
  const provider = getBrowserProvider()
  if (!provider) return null
  try {
    const network = await provider.getNetwork()
    return `0x${network.chainId.toString(16)}`
  } catch {
    return null
  }
}

/**
 * 현재 체인 ID 를 10진수로 돌려준다. 실패 시 null.
 *
 * {@link getChainId} 와 나란히 두는 이유는 쓰임새가 다르기 때문이다 —
 * 저쪽은 화면에 그대로 찍는 표시용 16진수이고, 이쪽은 Backend DTO 용이다.
 * `wallet_nonce.chain_id` 가 BIGINT 라 hex 문자열을 보내면 서버가 파싱 규약을
 * 따로 정해야 한다. 지갑이 bigint 를 주므로 여기서 숫자로 바꿔 보낸다.
 *
 * 체인 ID 가 2^53 을 넘는 경우는 실무상 없으므로 Number 변환으로 충분하다.
 */
export async function getChainIdNumber(): Promise<number | null> {
  const provider = getBrowserProvider()
  if (!provider) return null
  try {
    return Number((await provider.getNetwork()).chainId)
  } catch {
    return null
  }
}

/**
 * 임의 메시지에 서명한다 (personal_sign).
 * Wallet Login 의 Nonce 서명이 이 함수를 쓴다.
 *
 * 사용자가 팝업에서 취소하면 SIGNATURE_REJECTED 로 정규화되어 던져진다.
 * 호출부는 그 종류만 보고 "취소했습니다" 를 안내하면 되고, EIP-1193 코드를 알 필요가 없다.
 */
export async function signMessage(message: string): Promise<string> {
  const signer = await getSigner()
  try {
    return await signer.signMessage(message)
  } catch (error) {
    throw toWalletError(error)
  }
}

/**
 * 던져진 값에서 EIP-1193 에러 코드를 꺼낸다.
 *
 * 두 군데를 봐야 한다.
 *
 *   1. `error.code` 가 숫자 — provider 가 직접 던진 raw EIP-1193 에러.
 *      walletClient.ts 의 연결 경로가 이 모양을 받는다.
 *   2. `error.info.error.code` — **ethers 가 감싼 경우**. ethers v6 는 provider 에러를
 *      자기 형식으로 바꾸면서 최상위 `code` 를 문자열('ACTION_REJECTED' 등)로 덮어쓰고
 *      원본 숫자 코드를 info 아래로 밀어 넣는다. 서명·트랜잭션 경로가 전부 여기 해당한다.
 *
 * 1번만 보면 서명 취소(4001)가 UNKNOWN 으로 떨어져 "알 수 없는 오류가 발생했습니다" 가
 * 뜬다. 사용자가 스스로 취소한 정상 흐름인데 장애처럼 보이게 된다.
 */
function errorCode(error: unknown): number | null {
  if (typeof error !== 'object' || error === null) return null

  const code = (error as { code?: unknown }).code
  if (typeof code === 'number') return code

  const nested = (error as { info?: { error?: { code?: unknown } } }).info?.error?.code
  if (typeof nested === 'number') return nested

  // ethers 가 숫자 코드를 잃어버린 경우의 마지막 보루.
  return code === 'ACTION_REJECTED' ? RPC_ERROR.USER_REJECTED : null
}

/**
 * 지갑/ethers 에러를 AppError 로 정규화한다.
 * Page 가 MetaMask 에러를 직접 파싱하지 않게 하는 것이 이 함수의 존재 이유다.
 */
export function toWalletError(error: unknown) {
  switch (errorCode(error)) {
    case RPC_ERROR.USER_REJECTED:
      return createAppError(ERROR_KIND.SIGNATURE_REJECTED, { cause: error })
    case RPC_ERROR.UNRECOGNIZED_CHAIN:
      return createAppError(ERROR_KIND.WRONG_NETWORK, { cause: error })
    case RPC_ERROR.ALREADY_PENDING:
      return createAppError(ERROR_KIND.WALLET_DISCONNECTED, {
        message: 'MetaMask 창에서 이전 요청을 먼저 처리해 주세요.',
        cause: error,
      })
    default:
      return createAppError(ERROR_KIND.UNKNOWN, { cause: error })
  }
}
