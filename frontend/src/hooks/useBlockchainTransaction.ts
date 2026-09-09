// 블록체인 트랜잭션 UX 를 한 곳에 모은 훅.
//
// Material Mint, Token Transfer, Processing 결과 기록, Production 등록 —
// 기능은 다르지만 사용자가 겪는 흐름은 전부 같다.
//
//   버튼 클릭 → MetaMask 팝업(서명) → 전송 → 블록 채굴 대기 → 성공/실패
//
// 이걸 각 도메인이 따로 구현하면 화면마다 로딩 표시와 취소 처리가 미묘하게 달라진다.
// 그래서 상태 기계를 여기에 한 번만 두고, 도메인은 "무엇을 보낼지"만 넘긴다.

import { useCallback, useEffect, useRef, useState } from 'react'
import type { ContractTransactionReceipt, ContractTransactionResponse } from 'ethers'
import { toWalletError } from '../features/wallet/services/walletService.ts'
import { ERROR_KIND, isAppError, type AppError } from '../utils/errors.ts'

export type TransactionStatus =
  /** 아직 시작하지 않음 */
  | 'IDLE'
  /** MetaMask 팝업이 떠 있고 사용자의 서명을 기다리는 중 */
  | 'SIGNING'
  /** 전송됨. 블록에 포함되기를 기다리는 중 (해시는 이미 있다) */
  | 'PENDING'
  /** receipt 확인 완료 */
  | 'CONFIRMED'
  /** 서명 거부, 전송 실패, revert 등 */
  | 'FAILED'

export interface TransactionState {
  status: TransactionStatus
  /** PENDING 부터 값이 생긴다. 사용자에게 익스플로러 링크를 보여줄 때 쓴다. */
  hash: string | null
  receipt: ContractTransactionReceipt | null
  error: AppError | null
}

const INITIAL_STATE: TransactionState = {
  status: 'IDLE',
  hash: null,
  receipt: null,
  error: null,
}

/**
 * 트랜잭션을 보내는 함수. 도메인의 Contract Service 가 이 형태로 제공한다.
 * 예) () => materialContractService.mint(materialId, quantity)
 */
export type SendTransaction = () => Promise<ContractTransactionResponse>

export interface UseBlockchainTransactionOptions {
  /** 몇 개의 확인 블록을 기다릴지. 기본 1. */
  confirmations?: number
  onConfirmed?: (receipt: ContractTransactionReceipt) => void | Promise<void>
  onFailed?: (error: AppError) => void
}

export interface UseBlockchainTransactionResult extends TransactionState {
  /** 트랜잭션 실행. 성공 영수증을 돌려주고 실패하면 null이다. */
  execute: (send: SendTransaction) => Promise<ContractTransactionReceipt | null>
  reset: () => void
  /** SIGNING 또는 PENDING — 버튼을 잠글 때 쓴다. */
  isBusy: boolean
  /** 사용자가 서명을 취소한 경우. 에러 배너 대신 조용히 넘기고 싶을 때 구분한다. */
  isRejected: boolean
}

export function useBlockchainTransaction(
  options: UseBlockchainTransactionOptions = {},
): UseBlockchainTransactionResult {
  const { confirmations = 1, onConfirmed, onFailed } = options
  const [state, setState] = useState<TransactionState>(INITIAL_STATE)

  // 콜백을 ref 로 들고 있어 execute 의 의존성에서 빼낸다.
  // 그러지 않으면 호출부가 인라인 함수를 넘길 때마다 execute 의 참조가 바뀌어,
  // 이 훅을 의존성에 넣은 useEffect 나 useCallback 이 매 렌더 재실행된다.
  //
  // 갱신을 effect 안에서 하는 이유: 렌더 중에 ref 를 쓰면 React 가 렌더를 버리고
  // 다시 실행했을 때(Concurrent 렌더) 실제로 커밋되지 않은 값이 남는다.
  // 콜백은 await 이후에만 호출되므로 커밋 이후 시점의 값이면 충분하다.
  const callbacks = useRef<UseBlockchainTransactionOptions>({ onConfirmed, onFailed })
  useEffect(() => {
    callbacks.current = { onConfirmed, onFailed }
  }, [onConfirmed, onFailed])

  // 중복 실행 방지. 사용자가 버튼을 빠르게 두 번 누르면 트랜잭션이 두 번 나가고,
  // 그건 되돌릴 수 없다. status 상태로 막으면 setState 반영 전 두 번째 클릭을 놓친다.
  const running = useRef(false)

  const reset = useCallback(() => {
    running.current = false
    setState(INITIAL_STATE)
  }, [])

  const execute = useCallback(
    async (send: SendTransaction) => {
      if (running.current) return null
      running.current = true
      setState({ ...INITIAL_STATE, status: 'SIGNING' })

      try {
        // 이 await 가 풀릴 때까지가 SIGNING 구간이다 (팝업이 떠 있는 시간).
        const response = await send()
        setState({ status: 'PENDING', hash: response.hash, receipt: null, error: null })

        const receipt = await response.wait(confirmations)

        // ethers 는 트랜잭션이 대체(replace)되거나 드롭되면 null 을 돌려준다.
        if (!receipt) {
          throw {
            kind: ERROR_KIND.TRANSACTION_FAILED,
            message: '트랜잭션이 취소되거나 대체되었습니다.',
            code: null,
            status: null,
            validationErrors: [],
            cause: null,
          } satisfies AppError
        }

        // status 0 = EVM revert. 전송은 됐지만 실패한 것이므로 성공으로 처리하면 안 된다.
        if (receipt.status === 0) {
          throw {
            kind: ERROR_KIND.TRANSACTION_FAILED,
            message: '트랜잭션이 체인에서 되돌려졌습니다.',
            code: null,
            status: null,
            validationErrors: [],
            cause: receipt,
          } satisfies AppError
        }

        setState({ status: 'CONFIRMED', hash: receipt.hash, receipt, error: null })
        // 후속 서버 연결이 있다면 그것까지 끝나야 호출부가 안전하게 화면을 이동할 수 있다.
        await callbacks.current.onConfirmed?.(receipt)
        return receipt
      } catch (raw) {
        // 서명 거부(4001)든 RPC 실패든 여기서 AppError 로 통일한다.
        // 화면은 error.kind 만 보면 되고 ethers 내부 형태를 알 필요가 없다.
        const error = isAppError(raw) ? raw : toWalletError(raw)
        setState((prev) => ({ ...prev, status: 'FAILED', error }))
        callbacks.current.onFailed?.(error)
        return null
      } finally {
        running.current = false
      }
    },
    [confirmations],
  )

  return {
    ...state,
    execute,
    reset,
    isBusy: state.status === 'SIGNING' || state.status === 'PENDING',
    isRejected: state.error?.kind === ERROR_KIND.SIGNATURE_REJECTED,
  }
}
