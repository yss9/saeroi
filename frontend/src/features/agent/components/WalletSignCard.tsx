import { useState } from 'react'
import { useBlockchainTransaction } from '../../../hooks/useBlockchainTransaction.ts'
import { getWalletAddress } from '../../wallet/services/walletService.ts'
import { useCompleteAction, useRejectAction } from '../hooks/useAgentConversation.ts'
import { StatusOrActions } from './PaymentCard.tsx'
import type { ChainOrderPayload, PendingAction } from '../types/agent.types.ts'

/**
 * 지갑 서명 카드.
 *
 * <h2>서버가 체인에 쓰지 않는다</h2>
 *
 * 서명은 브라우저의 개인키로만 가능하다. 실제 컨트랙트 호출은 기존
 * `*ContractService` 가 하고, 그 결과를 기존 chain-link API 로 서버에 붙인다 —
 * 화면에서 하던 것과 <b>같은 경로</b>다. 에이전트용 경로를 따로 만들면 두 벌이 되고,
 * 어긋나는 순간 어느 쪽이 정본인지 알 수 없게 된다.
 *
 * <h2>다단계 서명</h2>
 *
 * 생산 확정처럼 컨트랙트가 순서를 강제하는 연속 서명은 카드 하나에 `stepTotal` 로 묶인다.
 * <b>다음에 할 단계는 `stepDone` 이 아니라 온체인 상태로 정해야 한다</b> — 트랜잭션은
 * 성공했는데 서버 보고가 실패하면 `stepDone` 이 체인보다 뒤처지고, 그걸 믿으면 이미 끝난
 * 단계를 다시 서명해 revert 한다.
 *
 * 지금 슬라이스(소재 거래)의 서명은 전부 단발(`stepTotal === 1`)이라 그 분기가 아직
 * 필요 없다. 생산 단계를 붙일 때 `onResolveNextStep` 으로 체인 조회를 주입한다.
 */
export function WalletSignCard({
  action,
  onSign,
}: {
  action: PendingAction
  /** 실제 서명을 수행하고 txHash 를 돌려준다. 도메인별 ContractService 를 호출한다. */
  onSign: (payload: ChainOrderPayload) => Promise<string>
}) {
  const complete = useCompleteAction()
  const reject = useRejectAction()
  const transaction = useBlockchainTransaction()
  const [error, setError] = useState<string | null>(null)

  const payload = parsePayload(action.payloadJson)

  async function handleSign() {
    if (!payload) {
      setError('서명할 내용을 읽지 못했습니다. 대화에서 다시 요청해 주세요.')
      return
    }
    setError(null)

    // **서명 전에 지갑이 열려 있는지 먼저 본다.**
    //
    // MetaMask 가 잠겨 있으면 요청이 확장 아이콘의 배지로 대기하는데, 이 화면은 별도
    // 팝업 창이라 그 아이콘이 보이지 않는다. 담당자는 아무 반응 없는 화면만 보게 된다.
    // listAccounts 는 팝업을 띄우지 않으므로 이 확인 자체는 부작용이 없다.
    const connected = await getWalletAddress()
    if (!connected) {
      setError(
        'MetaMask 가 잠겨 있거나 이 사이트에 연결되어 있지 않습니다. ' +
          '확장 프로그램을 열어 잠금을 해제한 뒤 다시 눌러 주세요.',
      )
      return
    }

    try {
      const txHash = await onSign(payload)
      await complete.mutateAsync({ actionId: action.id, resultJson: JSON.stringify({ txHash }) })
    } catch (signError) {
      // 사용자가 팝업에서 거절한 것(4001)은 오류가 아니라 선택이다. 카드를 닫지 않고
      // 그대로 두어 다시 누를 수 있게 한다.
      if (isUserRejection(signError)) {
        setError('서명을 취소했습니다. 다시 진행하시려면 버튼을 눌러 주세요.')
        return
      }

      // **실제 이유를 그대로 보여준다.** 서명 창이 뜨기 전에 막히는 경우가 여럿이고
      // (지갑 미연결, 체인 불일치, 온체인 BRAND 권한 없음, 판매 글 온체인 미등록),
      // "지갑 상태를 확인하세요" 로 뭉뚱그리면 담당자도 개발자도 무엇이 문제인지 모른다.
      // **이미 연결된 건은 실패가 아니다.** 첫 서명이 성공했는데 화면이 그 사실을 못
      // 보여주면 담당자가 다시 누르고, 그때 서버가 BLOCKCHAIN_ALREADY_LINKED 로 거절한다.
      // 그걸 오류로 보여주면 성공한 작업이 실패한 것처럼 남는다.
      if (isAlreadyLinked(signError)) {
        await complete.mutateAsync({ actionId: action.id, resultJson: '{"alreadyLinked":true}' })
        return
      }
      // 체인이 이미 그 상태인 경우. 서버에 붙일 것이 없는 서명(수령 확인 등)은
      // chain-link API 를 지나지 않아 위 코드로 오지 않으므로, 서명자가 직접 알린다.
      if (isChainAlreadyDone(signError)) {
        await complete.mutateAsync({ actionId: action.id, resultJson: '{"alreadyReceived":true}' })
        return
      }
      console.error('[agent] 서명 실패', signError)
      setError(messageOf(signError))
    }
  }

  const busy = complete.isPending || transaction.status === 'PENDING'
  const disabled = action.status !== 'PENDING' || busy

  return (
    <div className="agent-card agent-card--sign">
      <p className="agent-card__title">
        지갑 서명
        {action.stepTotal > 1 && ` — ${action.stepDone}/${action.stepTotal}단계`}
      </p>

      <p className="agent-card__detail">{payload?.summary ?? action.summary}</p>

      {action.stepTotal > 1 && (
        <ol className="agent-card__steps">
          {Array.from({ length: action.stepTotal }, (_, index) => (
            <li key={index} className={index < action.stepDone ? 'is-done' : undefined}>
              {index + 1}단계 {index < action.stepDone ? '완료' : ''}
            </li>
          ))}
        </ol>
      )}

      <StatusOrActions
        action={action}
        disabled={disabled}
        busyLabel="지갑 확인 중…"
        confirmLabel={action.stepTotal > 1 ? '계속 서명' : '서명하기'}
        onConfirm={handleSign}
        isBusy={busy}
        onReject={() => void reject.mutateAsync(action.id)}
      />

      {error && <p className="agent-card__error">{error}</p>}
    </div>
  )
}

function parsePayload(payloadJson: string): ChainOrderPayload | null {
  try {
    return JSON.parse(payloadJson) as ChainOrderPayload
  } catch {
    return null
  }
}

/**
 * 오류에서 사람이 읽을 문장을 꺼낸다.
 *
 * 앱의 `AppError` 도, ethers 가 던지는 오류도, 우리가 던진 `Error` 도 모두 `message` 를
 * 갖고 있어 그걸 그대로 쓴다. 없으면 최소한의 안내라도 남긴다.
 */
function messageOf(error: unknown): string {
  if (error instanceof Error && error.message) {
    return error.message
  }
  if (typeof error === 'object' && error !== null && 'message' in error) {
    const message = (error as { message: unknown }).message
    if (typeof message === 'string' && message) return message
  }
  return '서명에 실패했습니다. 브라우저 콘솔에서 자세한 내용을 확인해 주세요.'
}

/**
 * 이미 온체인에 연결된 건인가.
 *
 * 같은 거래를 두 번 기록할 수는 없으므로, 서버가 이렇게 답했다면 <b>앞선 시도가
 * 성공했다는 뜻</b>이다. 카드를 완료로 닫는 것이 사실에 맞는다.
 */
function isAlreadyLinked(error: unknown): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    'code' in error &&
    (error as { code: unknown }).code === 'BLOCKCHAIN_ALREADY_LINKED'
  )
}

/**
 * 체인이 이미 그 상태인가.
 *
 * 서명자가 미리 체인을 읽어 판단한 경우다. 다시 서명하면 컨트랙트가 revert 하는데, 그건
 * 실패가 아니라 <b>앞선 시도가 성공했다는 뜻</b>이다. 오류로 보여주면 성공한 작업이
 * 실패한 것처럼 남는다.
 */
function isChainAlreadyDone(error: unknown): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    'code' in error &&
    (error as { code: unknown }).code === 'CHAIN_ALREADY_DONE'
  )
}

/** MetaMask 가 사용자 거절에 쓰는 코드. */
function isUserRejection(error: unknown): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    'code' in error &&
    (error as { code: unknown }).code === 4001
  )
}
