import type { ContractTransactionReceipt, ContractTransactionResponse } from 'ethers'
import { getBrowserProvider } from '../../wallet/services/walletService.ts'

/**
 * 가공 작업을 체인에 **두 번 만들지 않는다.**
 *
 * <h2>왜 필요한가</h2>
 *
 * 등록은 두 걸음이다 — 체인에 작업을 만들고(서명), 그 번호를 서버에 알린다(linkChain). 그 사이에
 * 서버 호출이 실패하는 창이 있다(네트워크 끊김, 로그인 만료). 그때 다시 누르면 서버는 아직 체인
 * 작업을 모르므로 한 번 더 만들게 되고, **앞의 작업은 소재를 체인에 잠근 채 버려진다.**
 *
 * 그래서 보낸 거래를 **확정을 기다리기 전에** 기억해 두고, 다시 누르면 새로 만들지 않고 그 거래의
 * 영수증에서 작업 번호를 읽어 이어 간다. 대화창과 가공 화면이 같은 기억을 쓴다 — 한쪽에서 시작해
 * 다른 쪽에서 다시 눌러도 이어진다.
 *
 * 기억은 이 브라우저에만 남는다. 다른 기기에서 다시 누르는 경우까지 막지는 못하지만, 이 실패가
 * 일어나는 흔한 경우는 같은 창에서 곧바로 다시 누르는 것이다.
 */
export async function createOrResumeProcessingChainJob(
  jobId: number,
  send: () => Promise<ContractTransactionResponse>,
): Promise<ContractTransactionReceipt> {
  return (await resume(jobId)) ?? (await create(jobId, send))
}

/** 서버에 연결까지 끝났으면 기억을 지운다. 서버가 이미 체인 작업을 알 때도 부른다. */
export function forgetProcessingChainCreate(jobId: number) {
  try {
    window.localStorage.removeItem(key(jobId))
  } catch {
    /* 지우지 못해도 다음 시도에서 영수증을 다시 확인한다 */
  }
}

function key(jobId: number): string {
  return `processing-chain-create:${jobId}`
}

/** 거래를 보내고, 확정을 기다리기 전에 해시를 기억한다. 기다리는 사이에 창이 닫혀도 이어 갈 수 있다. */
async function create(
  jobId: number,
  send: () => Promise<ContractTransactionResponse>,
): Promise<ContractTransactionReceipt> {
  const transaction = await send()
  remember(jobId, transaction.hash)
  const receipt = await transaction.wait()
  if (!receipt || receipt.status !== 1) {
    // 체인이 되돌린 거래는 이어 갈 것이 없다. 다음 시도는 새로 만든다.
    forgetProcessingChainCreate(jobId)
    throw new Error('이력 저장이 완료되지 않았습니다.')
  }
  return receipt
}

/**
 * 앞서 보낸 등록 거래의 영수증. 기억한 것이 없거나 그 거래가 체인에서 실패했으면 `null` — 새로
 * 만들면 된다.
 *
 * **아직 확정되지 않았으면 새로 만들지 않고 멈춘다.** 기다리지 않고 만들면 앞의 거래가 곧 확정되면서
 * 작업이 두 개가 된다.
 */
async function resume(jobId: number): Promise<ContractTransactionReceipt | null> {
  const hash = recall(jobId)
  if (!hash) return null
  const provider = getBrowserProvider()
  if (!provider) return null

  const receipt = await provider.waitForTransaction(hash, 1, 60_000)
  if (!receipt) {
    throw new Error('앞서 보낸 등록이 아직 확인되지 않았습니다. 잠시 뒤 다시 눌러 주세요.')
  }
  if (receipt.status !== 1) {
    forgetProcessingChainCreate(jobId)
    return null
  }
  // 영수증에서 쓰는 것은 로그뿐이다 — 거래를 보낸 쪽 영수증과 모양이 같다.
  return receipt as ContractTransactionReceipt
}

// 저장소를 못 쓰는 환경(사생활 보호 창 등)에서는 기억하지 못할 뿐, 등록은 그대로 된다.
function remember(jobId: number, hash: string) {
  try {
    window.localStorage.setItem(key(jobId), hash)
  } catch {
    /* 기억하지 못해도 등록은 계속한다 */
  }
}

function recall(jobId: number): string | null {
  try {
    return window.localStorage.getItem(key(jobId))
  } catch {
    return null
  }
}
