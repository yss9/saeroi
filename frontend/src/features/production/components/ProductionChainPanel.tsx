import { OnChainPanel } from '../../../components/common/index.ts'
import { contracts, isContractConfigured } from '../../../config/contracts.ts'
import { env } from '../../../config/env.ts'
import { useWallet } from '../../wallet/hooks/useWallet.ts'
import { useCreateChainBatch } from '../hooks/useCreateChainBatch.ts'
import { ProductionChainSteps } from './ProductionChainSteps.tsx'
import type { ProductionBatch } from '../types/production.types.ts'
import './production.css'

export interface ProductionChainPanelProps {
  batch: ProductionBatch
}

/**
 * 생산의 온체인 기록.
 *
 * **왜 필요한가** — 소재 발행·판매·거래는 이미 체인에 남는데 생산만 오프체인 DB 에만
 * 있었다. 그러면 QR Passport 가 증명하는 범위가 소재와 거래에서 멈추고, "이 제품이 그
 * 소재로 만들어졌다" 는 마지막 한 칸은 서버 말을 믿어야 한다.
 *
 * **온체인은 4단계고 서버가 순서를 강제한다** — 앞 단계를 알리지 않으면 다음 보고가
 * `PRODUCTION_CHAIN_BATCH_NOT_LINKED` 로 거절된다.
 *
 *   1) `createBatch`        온체인 배치 번호 채번        ← 이 패널이 서명하는 단계
 *   2) `submitPlanAndLock`  투입 잠금 + 계획 해시 기록
 *   3) `confirmProduction`  소재 소각
 *   4) `linkProductItems`   개체 연결
 *
 * **네 단계가 모두 배선돼 있다.** 1단계는 이 패널이 서명하고(번호를 채번하는 단계라 모양이
 * 다르다), 2~4단계는 `ProductionChainSteps` 가 맡는다 — 이미 있는 배치를 앞으로 미는
 * 단계들이라 다음에 할 수 있는 일을 체인 상태에서 읽는다.
 */
export function ProductionChainPanel({ batch }: ProductionChainPanelProps) {
  const { address } = useWallet()
  const chain = useCreateChainBatch(batch.productionBatchId, batch.producedQuantity)

  // 확정 전에는 투입이 더 바뀔 수 있다. 바뀐 뒤에도 체인에는 옛 내용이 남고 그 어긋남은
  // 대조할 때만 드러난다 — 서버도 확정된 배치만 받는다.
  const notConfirmed = batch.status !== 'CONFIRMED'
  const configured = isContractConfigured('productionRegistry')

  // 배치가 서명자 주소를 스냅샷으로 갖고 있다. 다른 계정으로 서명하면 컨트랙트의
  // BRAND_ROLE 검사는 통과해도 배치에 적힌 주소와 어긋난 기록이 남는다.
  const walletMismatch =
    address !== null && address.toLowerCase() !== batch.producerWallet.toLowerCase()

  const ready = !notConfirmed && configured && address !== null && !walletMismatch

  return (
    <OnChainPanel
      title="생산 온체인 기록"
      description={`제품 ${batch.producedQuantity}개의 생산 배치를 체인에 만듭니다. 뒤의 단계가 모두 이 번호를 사용하므로 먼저 해야 합니다.`}
      onChainId={batch.chainBatchId}
      actionable
      ready={ready}
      blockedReason={blockedReason({ notConfirmed, configured, address, walletMismatch, batch })}
      status={chain.status}
      hash={chain.hash}
      blockNumber={chain.blockNumber}
      walletAddress={address}
      chainId={env.besuChainIdHex}
      contractAddress={contracts.productionRegistry}
      error={chain.error}
      isBusy={chain.isBusy}
      isRejected={chain.isRejected}
      onSign={() => void chain.create()}
      actionLabel="온체인 배치 생성"
    >
      <ProductionChainSteps batch={batch} />
    </OnChainPanel>
  )
}

/**
 * 못 누르는 이유.
 *
 * 버튼만 잠그면 사용자는 자기가 뭘 잘못했다고 생각한다. 순서대로 본다 — 확정 여부가
 * 먼저이고, 지갑 문제를 그 앞에 두면 확정하지 않은 배치에서 "지갑을 연결하라" 는 엉뚱한
 * 안내가 나간다.
 */
function blockedReason({
  notConfirmed,
  configured,
  address,
  walletMismatch,
  batch,
}: {
  notConfirmed: boolean
  configured: boolean
  address: string | null
  walletMismatch: boolean
  batch: ProductionBatch
}): string | undefined {
  if (notConfirmed) {
    return '배치를 확정하면 온체인에 기록할 수 있습니다. 확정 전에는 투입이 더 바뀔 수 있어 체인에 남긴 내용과 어긋납니다.'
  }
  if (!configured) {
    return '생산 컨트랙트 주소가 설정되지 않아 온체인 기록을 할 수 없습니다. 운영자에게 알려 주세요.'
  }
  if (address === null) return '지갑을 먼저 연결해 주세요.'
  if (walletMismatch) {
    return `이 배치는 ${shorten(batch.producerWallet)} 지갑으로 열렸습니다. 그 계정으로 연결해 주세요 — 다른 계정으로 서명하면 배치에 적힌 주소와 어긋난 기록이 남습니다.`
  }
  return undefined
}

/** 주소를 짧게. 전체를 적으면 문장이 읽히지 않는다. */
function shorten(address: string): string {
  return `${address.slice(0, 6)}…${address.slice(-4)}`
}
