// "서버에 만들고 → 지갑으로 서명하고 → 그 결과를 서버에 알린다" 를 한 번만 구현한다.
//
// 소재 발행·판매 등록·주문 생성이 전부 같은 모양이다. 다른 것은 세 가지뿐이다 —
// 어떤 컨트랙트 함수를 부르는지, 어떤 이벤트에서 번호를 꺼내는지, 어느 경로로 알리는지.
//
// **왜 서버가 먼저인가**
//   체인은 되돌릴 수 없다. 체인을 먼저 쓰고 서버 저장이 실패하면 우리가 가리키지 못하는
//   토큰이 남는다. 서버를 먼저 쓰면 실패해도 chain_id 가 NULL 인 행이 남을 뿐이고,
//   그것은 이미 정의된 "등록됐지만 아직 온체인에 없음" 상태다
//   (MaterialRegistrationService 주석). 나중에 그 행만 골라 다시 서명하면 된다.
//
// **왜 execute 의 반환값을 쓰지 않는가**
//   useBlockchainTransaction.execute 는 Promise<void> 이고 receipt 를 state 로만 알린다.
//   await 직후에 transaction.receipt 를 읽으면 이번 렌더의 옛 값이라 언제나 null 이다.
//   그래서 onConfirmed 콜백에서 이어 붙이고, 어느 대상에 붙일지는 ref 로 넘긴다.

import { useCallback, useEffect, useRef, useState } from 'react'
import type { ContractTransactionReceipt, ContractTransactionResponse } from 'ethers'
import type { ContractName } from '../config/contracts.ts'
import { readEventArg } from '../services/blockchain/contractFactory.ts'
import type { ChainLinkPayload } from '../services/blockchain/chainLinkApi.ts'
import { useBlockchainTransaction, type TransactionStatus } from './useBlockchainTransaction.ts'
import { ERROR_KIND, createAppError, isAppError, type AppError } from '../utils/errors.ts'

/** 서명 이후 단계까지 포함한 상태. 트랜잭션 상태만으로는 서버 기록 실패를 표현할 수 없다. */
export type ChainLinkStatus = TransactionStatus | 'LINKING' | 'LINKED'

export interface UseOnChainLinkOptions {
  /** 이벤트를 해석할 컨트랙트. ABI 를 고르는 데만 쓴다. */
  contract: ContractName
  /** 번호가 실려 오는 이벤트 (`MaterialMinted` 등). */
  eventName: string
  /** 그 이벤트에서 꺼낼 인자 이름 (`tokenId` 등). */
  argName: string
  /** 꺼낸 번호를 서버에 알리는 함수. */
  link: (targetId: number, payload: ChainLinkPayload) => Promise<void>
  onLinked?: (onChainId: number) => void
}

export interface UseOnChainLinkResult {
  status: ChainLinkStatus
  hash: string | null
  /** 영수증이 확정된 블록. 사용자가 지갑/익스플로러 기록과 대조할 때 쓴다. */
  blockNumber: number | null
  error: AppError | null
  /** SIGNING·PENDING·LINKING — 버튼을 잠글 때 쓴다. */
  isBusy: boolean
  /** 사용자가 서명을 취소했다. 에러 배너 대신 조용히 넘기고 싶을 때 구분한다. */
  isRejected: boolean
  /** 서명 → 확정 → 서버 기록. **던지지 않는다** — 결과는 상태로만 알린다. */
  run: (targetId: number, send: () => Promise<ContractTransactionResponse>) => Promise<void>
  reset: () => void
}

type LinkPhase = { status: 'IDLE' | 'LINKING' | 'LINKED'; error: AppError | null }

const IDLE: LinkPhase = { status: 'IDLE', error: null }

export function useOnChainLink(options: UseOnChainLinkOptions): UseOnChainLinkResult {
  const { contract, eventName, argName, link, onLinked } = options

  // 서버 기록 구간은 트랜잭션 훅이 모르므로 따로 든다.
  const [phase, setPhase] = useState<LinkPhase>(IDLE)

  // 확정 콜백은 run 의 인자를 볼 수 없다. 대상 id 를 ref 로 건네준다.
  const targetId = useRef<number | null>(null)

  // 콜백이 매 렌더 새로 만들어져도 훅이 다시 만들어지지 않게 ref 로 든다
  // (useBlockchainTransaction 이 내부에서 하는 것과 같은 이유).
  //
  // 갱신을 effect 안에서 하는 이유도 같다: 렌더 중에 ref 를 쓰면 React 가 렌더를 버리고
  // 다시 실행했을 때(Concurrent 렌더) 커밋되지 않은 값이 남는다. 여기서 쓰는 값은
  // 모두 await 이후에 읽히므로 커밋 이후 시점이면 충분하다.
  const latest = useRef({ contract, eventName, argName, link, onLinked })
  useEffect(() => {
    latest.current = { contract, eventName, argName, link, onLinked }
  }, [contract, eventName, argName, link, onLinked])

  const handleConfirmed = useCallback(async (receipt: ContractTransactionReceipt) => {
    const { contract, eventName, argName, link, onLinked } = latest.current
    const id = targetId.current
    if (id === null) return

    // 채번된 번호는 **이벤트에서** 꺼낸다. 트랜잭션으로 부르면 함수의 반환값이 영수증에
    // 남지 않기 때문이다 — 남는 것은 로그뿐이다.
    const onChainId = readEventArg<bigint>(receipt, contract, eventName, argName)
    if (onChainId === null) {
      // ABI 가 배포된 컨트랙트와 다르면 여기서 드러난다. 트랜잭션 자체는 성공했으므로
      // "실패" 가 아니라 무엇이 어긋났는지 말해 주는 편이 낫다.
      setPhase({
        status: 'IDLE',
        error: createAppError(ERROR_KIND.TRANSACTION_FAILED, {
          message: `${eventName} 이벤트를 찾지 못했습니다. 배포된 컨트랙트와 ABI 가 다를 수 있습니다.`,
        }),
      })
      return
    }

    setPhase({ status: 'LINKING', error: null })
    try {
      await link(id, {
        onChainId: Number(onChainId),
        txHash: receipt.hash,
        blockNumber: receipt.blockNumber ?? undefined,
      })
      setPhase({ status: 'LINKED', error: null })
      onLinked?.(Number(onChainId))
    } catch (error) {
      // **체인에는 남고 서버에만 없는 상태다.** 되돌릴 수 없으므로 사실대로 알린다 —
      // "실패" 로 뭉뚱그리면 사용자가 같은 서명을 다시 시도하고 토큰이 하나 더 생긴다.
      setPhase({
        status: 'IDLE',
        error: isAppError(error)
          ? { ...error, message: `온체인 등록은 끝났지만 서버 기록에 실패했습니다. ${error.message}` }
          : createAppError(ERROR_KIND.UNKNOWN, {
              message: '온체인 등록은 끝났지만 서버 기록에 실패했습니다.',
              cause: error,
            }),
      })
    }
  }, [])

  const transaction = useBlockchainTransaction({
    onConfirmed: (receipt) => {
      void handleConfirmed(receipt)
    },
  })

  const run = useCallback(
    async (id: number, send: () => Promise<ContractTransactionResponse>) => {
      // **기록할 수 없으면 서명시키지 않는다.**
      //
      // 체인 트랜잭션은 되돌릴 수 없다. 대상 id 가 깨진 채로 서명부터 시키면 발행은
      // 성공하고 기록만 실패해서, 우리가 가리키지 못하는 토큰이 체인에 남는다. 사용자가
      // 할 수 있는 일은 재시도뿐인데 그러면 토큰이 하나 더 생긴다.
      //
      // 2026-09-08 에 실제로 그렇게 됐다 — 화면이 undefined 를 넘겨
      // `/api/materials/undefined/chain` 이 500 을 냈고, 그때는 이미 mint 가 끝나 있었다.
      // 원인(타입 불일치)은 고쳤지만, 같은 종류의 실수가 다시 서명까지 가지 않도록 막는다.
      if (!Number.isInteger(id) || id <= 0) {
        setPhase({
          status: 'IDLE',
          error: createAppError(ERROR_KIND.UNKNOWN, {
            message: '대상을 특정할 수 없어 서명을 시작하지 않았습니다. 새로고침 후 다시 시도해 주세요.',
          }),
        })
        return
      }

      targetId.current = id
      setPhase(IDLE)
      await transaction.execute(send)
    },
    [transaction],
  )

  const reset = useCallback(() => {
    targetId.current = null
    setPhase(IDLE)
    transaction.reset()
  }, [transaction])

  return {
    status: phase.status === 'IDLE' ? transaction.status : phase.status,
    hash: transaction.hash,
    blockNumber: transaction.receipt?.blockNumber ?? null,
    error: phase.error ?? transaction.error,
    isBusy: transaction.isBusy || phase.status === 'LINKING',
    isRejected: transaction.isRejected,
    run,
    reset,
  }
}
