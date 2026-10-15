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
// 트랜잭션 영수증과 서버 연결 작업을 모두 기다린 뒤 결과를 돌려준다. 화면은 true일 때만
// 이동하므로, 서버 연결이 실패하면 같은 화면에서 이미 확정된 영수증으로 연결만 재시도한다.

import { useCallback, useEffect, useRef, useState } from 'react'
import type { ContractTransactionReceipt, ContractTransactionResponse } from 'ethers'
import type { ContractName } from '../config/contracts.ts'
import { readEventArg } from '../services/blockchain/contractFactory.ts'
import type { ChainLinkPayload } from '../services/blockchain/chainLinkApi.ts'
import { useBlockchainTransaction, type TransactionStatus } from './useBlockchainTransaction.ts'
import { ERROR_KIND, createAppError, isAppError, type AppError } from '../utils/errors.ts'
import {
  createBlockchainOperation,
  submitBlockchainTransaction,
  type BlockchainContract,
  type BlockchainReferenceType,
} from '../services/blockchain/operationApi.ts'

/** 서명 이후 단계까지 포함한 상태. 트랜잭션 상태만으로는 서버 기록 실패를 표현할 수 없다. */
export type ChainLinkStatus = TransactionStatus | 'LINKING' | 'LINKED'

export interface UseOnChainLinkOptions {
  /** 이벤트를 해석할 컨트랙트. ABI 를 고르는 데만 쓴다. */
  contract: ContractName
  /** 번호가 실려 오는 이벤트 (`MaterialMinted` 등). */
  eventName: string
  /** 그 이벤트에서 꺼낼 인자 이름 (`tokenId` 등). */
  argName: string
  operationContract: BlockchainContract
  functionName: string
  referenceType: BlockchainReferenceType
  /** 꺼낸 번호를 서버에 알리는 함수. */
  link: (targetId: number, payload: ChainLinkPayload) => Promise<void>
  onLinked?: (onChainId: number) => void
  /**
   * 서명하기 <b>전에</b> 체인에서 "이미 만들어져 있는지" 찾아보는 함수. 선택이다.
   *
   * <b>왜 필요한가</b> — 아래 {@code pendingLink} 는 서명은 끝났고 서버 기록만 실패한
   * 경우를 ref 로 붙들어 두지만, ref 는 메모리라 <b>새로고침하면 사라진다.</b> 그러면
   * 다음 시도가 다시 서명부터 보내 같은 것이 체인에 하나 더 생긴다. 되돌릴 수 없다.
   *
   * 체인이 정본이므로, 찾으면 서명을 건너뛰고 서버 기록만 이어서 한다.
   *
   * 못 찾거나 조회에 실패하면 {@code null} 을 주기로 한다 — 던지면 RPC 가 흔들릴 때
   * 정상 사용자가 아무것도 못 하게 된다.
   */
  findExisting?: (targetId: number) => Promise<ChainLinkPayload | null>
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
  /** 서명 → 확정 → 서버 기록. 세 단계가 모두 끝나면 true다. */
  run: (targetId: number, send: (operationId: string) => Promise<ContractTransactionResponse>) => Promise<boolean>
  reset: () => void
}

type LinkPhase = { status: 'IDLE' | 'LINKING' | 'LINKED'; error: AppError | null }

const IDLE: LinkPhase = { status: 'IDLE', error: null }

export function useOnChainLink(options: UseOnChainLinkOptions): UseOnChainLinkResult {
  const { contract, eventName, argName, operationContract, functionName, referenceType, link, onLinked, findExisting } = options

  // 서버 기록 구간은 트랜잭션 훅이 모르므로 따로 든다.
  const [phase, setPhase] = useState<LinkPhase>(IDLE)

  // 확정 콜백은 run 의 인자를 볼 수 없다. 대상 id 를 ref 로 건네준다.
  const targetId = useRef<number | null>(null)
  const operationId = useRef<string | null>(null)
  const linked = useRef(false)
  const pendingLink = useRef<{
    targetId: number
    onChainId: number
    payload: ChainLinkPayload
  } | null>(null)

  // 콜백이 매 렌더 새로 만들어져도 훅이 다시 만들어지지 않게 ref 로 든다
  // (useBlockchainTransaction 이 내부에서 하는 것과 같은 이유).
  //
  // 갱신을 effect 안에서 하는 이유도 같다: 렌더 중에 ref 를 쓰면 React 가 렌더를 버리고
  // 다시 실행했을 때(Concurrent 렌더) 커밋되지 않은 값이 남는다. 여기서 쓰는 값은
  // 모두 await 이후에 읽히므로 커밋 이후 시점이면 충분하다.
  const latest = useRef({ contract, eventName, argName, operationContract, functionName, referenceType, link, onLinked, findExisting })
  useEffect(() => {
    latest.current = { contract, eventName, argName, operationContract, functionName, referenceType, link, onLinked, findExisting }
  }, [contract, eventName, argName, operationContract, functionName, referenceType, link, onLinked, findExisting])

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
          message: '저장 결과를 확인하지 못했습니다. 관리자에게 문의해 주세요.',
        }),
      })
      return
    }

    const payload: ChainLinkPayload = {
      onChainId: Number(onChainId),
      txHash: receipt.hash,
      blockNumber: receipt.blockNumber ?? undefined,
    }

    // 체인 기록은 이미 확정됐다. 서버 연결이 실패해도 이 값을 보관해야 다음 클릭에서
    // mint/createOrder를 다시 보내지 않고 서버 연결만 안전하게 재시도할 수 있다.
    pendingLink.current = { targetId: id, onChainId: Number(onChainId), payload }
    setPhase({ status: 'LINKING', error: null })
    try {
      await link(id, payload)
      pendingLink.current = null
      linked.current = true
      setPhase({ status: 'LINKED', error: null })
      onLinked?.(Number(onChainId))
    } catch (error) {
      // **체인에는 남고 서버에만 없는 상태다.** 되돌릴 수 없으므로 사실대로 알린다 —
      // "실패" 로 뭉뚱그리면 사용자가 같은 서명을 다시 시도하고 토큰이 하나 더 생긴다.
      setPhase({
        status: 'IDLE',
        error: isAppError(error)
          ? { ...error, message: `이력은 저장되었지만 서비스에 반영하지 못했습니다. ${error.message}` }
          : createAppError(ERROR_KIND.UNKNOWN, {
              message: '이력은 저장되었지만 서비스에 반영하지 못했습니다.',
              cause: error,
            }),
      })
    }
  }, [])

  const transaction = useBlockchainTransaction({
    onSubmitted: async (hash) => {
      if (operationId.current !== null) {
        // 실패해도 체인 전송/영수증 대기는 계속한다. CREATED 작업을 워커가 operationId
        // 이벤트로 찾아 txHash까지 복구할 수 있기 때문이다.
        await submitBlockchainTransaction(operationId.current, hash).catch(() => undefined)
      }
    },
    // Promise를 그대로 반환해야 execute가 서버 연결 완료까지 기다린다.
    onConfirmed: handleConfirmed,
  })

  const run = useCallback(
    async (id: number, send: (operationId: string) => Promise<ContractTransactionResponse>) => {
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
            message: '기록할 대상을 확인할 수 없습니다. 새로고침 후 다시 시도해 주세요.',
          }),
        })
        return false
      }

      // **체인에 이미 있으면 서명하지 않는다.**
      //
      // pendingLink 는 새로고침을 견디지 못한다(ref = 메모리). 그래서 그것에만 의존하면
      // 서버 기록이 실패한 뒤 새로고침한 사용자가 다시 서명하게 되고, 같은 것이 체인에
      // 하나 더 생긴다. 체인이 정본이므로 먼저 물어본다.
      //
      // pendingLink 보다 먼저 보는 이유: 둘 다 같은 결론을 주지만, 체인 조회가 더
      // 믿을 만하다. 같은 세션 안이라면 pendingLink 가 곧바로 답을 주므로 왕복도 아깝지
      // 않다 — 아래 분기에서 잡힌다.
      const recovery =
        pendingLink.current?.targetId === id
          ? pendingLink.current
          : await (async () => {
              const found = await latest.current.findExisting?.(id)
              return found === null || found === undefined
                ? null
                : { targetId: id, onChainId: found.onChainId, payload: found }
            })()

      if (recovery?.targetId === id) {
        setPhase({ status: 'LINKING', error: null })
        try {
          await latest.current.link(id, recovery.payload)
          pendingLink.current = null
          linked.current = true
          setPhase({ status: 'LINKED', error: null })
          latest.current.onLinked?.(recovery.onChainId)
          return true
        } catch (error) {
          setPhase({
            status: 'IDLE',
            error: isAppError(error)
              ? {
                  ...error,
                  message: `이력은 저장되었지만 서비스에 반영하지 못했습니다. ${error.message}`,
                }
              : createAppError(ERROR_KIND.UNKNOWN, {
                  message: '이력은 저장되었지만 서비스에 반영하지 못했습니다.',
                  cause: error,
                }),
          })
          return false
        }
      }

      targetId.current = id
      linked.current = false
      setPhase(IDLE)
      const operation = await createBlockchainOperation({
        contract: latest.current.operationContract,
        functionName: latest.current.functionName,
        referenceType: latest.current.referenceType,
        referenceId: id,
      })
      operationId.current = operation.operationId
      const receipt = await transaction.execute(() => send(operation.operationId))
      return receipt !== null && linked.current
    },
    [transaction],
  )

  const reset = useCallback(() => {
    targetId.current = null
    operationId.current = null
    linked.current = false
    pendingLink.current = null
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
