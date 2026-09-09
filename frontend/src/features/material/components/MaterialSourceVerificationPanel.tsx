import { OnChainPanel } from '../../../components/common/index.ts'
import { contracts } from '../../../config/contracts.ts'
import { env } from '../../../config/env.ts'
import { useAuth } from '../../auth/hooks/useAuth.ts'
import { useWallet } from '../../wallet/hooks/useWallet.ts'
import { useVerifySourceOnChain } from '../hooks/useVerifySourceOnChain.ts'
import { evidencePlaceholder } from '../services/evidencePlaceholder.ts'
import type { MaterialDetail } from '../types/material.types.ts'

/**
 * 이미 발행된 소재의 출처를 온체인에서 검증한다 (S15P21E104-171/-182).
 *
 * <b>{@link MaterialMintPanel} 의 다음 단계다.</b> mint 는 "온체인에 없음 → 있음" 을
 * 다루고, 이 패널은 "발행됨 → 출처 검증됨" 을 다룬다. 그래서 `ready` 조건이 mint 와
 * 정반대다 — `tokenId` 가 **있어야** 서명할 수 있다.
 *
 * `evidenceHash` 는 mint 때와 같은 자리표시자를 재사용한다 — 같은 소재 코드에서
 * 파생하므로 재시도해도 같은 값이 나온다(`evidencePlaceholder` 주석 참고).
 */
export function MaterialSourceVerificationPanel({ material }: { material: MaterialDetail }) {
  const { user } = useAuth()
  // MaterialMintPanel 과 같은 이유로 스토어가 아니라 useWallet 을 쓴다 — 화면에
  // 지갑 연결 컴포넌트가 없으면 store 가 비어 있어 서명 버튼이 영영 잠긴다.
  const wallet = useWallet()
  const address = wallet.address
  const chainId = wallet.chainId
  const verify = useVerifySourceOnChain()

  const alreadyVerified = material.verification !== null
  const actionable = user?.id === material.registrantId
  const correctChain = env.besuChainIdHex !== null && chainId === env.besuChainIdHex
  const ready =
    actionable &&
    address !== null &&
    material.tokenId !== null &&
    !alreadyVerified &&
    correctChain &&
    contracts.materialToken !== null

  const blockedReason = (() => {
    if (material.tokenId === null) return '먼저 소재를 온체인에 발행해야 합니다.'
    if (address === null) return '지갑을 먼저 연결해 주세요.'
    if (env.besuChainIdHex === null) return 'SSAFY 네트워크 Chain ID 설정이 필요합니다.'
    if (!correctChain) {
      return `MetaMask를 SSAFY 네트워크(${BigInt(env.besuChainIdHex).toString()})로 전환해 주세요.`
    }
    if (contracts.materialToken === null) return 'MaterialToken 컨트랙트 주소 설정이 필요합니다.'
    return undefined
  })()

  return (
    <OnChainPanel
      title="출처 검증"
      description="등록된 출처가 사실임을 지갑 서명으로 확정합니다."
      // OnChainPanel 은 `onChainId` 로 완료 여부를 판단한다. 이 단계에는 새로 채번되는
      // 번호가 없으므로 이미 검증됐다는 사실 자체를 그 자리에 대신 넣는다.
      onChainId={alreadyVerified ? material.tokenId : null}
      actionable={actionable}
      ready={ready}
      blockedReason={blockedReason}
      status={verify.status}
      hash={verify.hash}
      blockNumber={verify.receipt?.blockNumber ?? null}
      walletAddress={address}
      chainId={chainId}
      contractAddress={contracts.materialToken}
      error={verify.error}
      isBusy={verify.isBusy}
      isRejected={verify.isRejected}
      actionLabel="출처 검증 서명"
      onSign={() => {
        if (!address || material.tokenId === null) return
        void verify.verify({
          materialId: material.materialId,
          tokenId: material.tokenId,
          evidenceHash: evidencePlaceholder(material.materialCode),
        })
      }}
    />
  )
}
