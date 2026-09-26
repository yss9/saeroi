import { useEffect, useState } from 'react'
import { OnChainPanel } from '../../../components/common/index.ts'
import { contracts } from '../../../config/contracts.ts'
import { env } from '../../../config/env.ts'
import { useAuth } from '../../auth/hooks/useAuth.ts'
import { useWallet } from '../../wallet/hooks/useWallet.ts'
import { useVerifySourceOnChain } from '../hooks/useVerifySourceOnChain.ts'
import { getOnChainMaterialEvidenceHash } from '../services/materialContractService.ts'
import type { MaterialDetail } from '../types/material.types.ts'

/**
 * 이미 발행된 소재의 출처를 온체인에서 검증한다 (S15P21E104-171/-182).
 *
 * <b>{@link MaterialMintPanel} 의 다음 단계다.</b> mint 는 "온체인에 없음 → 있음" 을
 * 다루고, 이 패널은 "발행됨 → 출처 검증됨" 을 다룬다. 그래서 `ready` 조건이 mint 와
 * 정반대다 — `tokenId` 가 **있어야** 서명할 수 있다.
 *
 * 검증에 넣는 해시는 DB나 현재 증빙 목록에서 다시 추측하지 않는다. mint 때 컨트랙트의
 * `materialInfo`에 저장된 값을 읽어 그대로 사용해야 사진 기반 발행도 hash mismatch로
 * revert되지 않는다.
 */
export function MaterialSourceVerificationPanel({ material }: { material: MaterialDetail }) {
  const { user } = useAuth()
  // MaterialMintPanel 과 같은 이유로 스토어가 아니라 useWallet 을 쓴다 — 화면에
  // 지갑 연결 컴포넌트가 없으면 store 가 비어 있어 서명 버튼이 영영 잠긴다.
  const wallet = useWallet()
  const address = wallet.address
  const chainId = wallet.chainId
  const verify = useVerifySourceOnChain()
  const [onChainEvidence, setOnChainEvidence] = useState<{
    tokenId: number
    hash: string
  } | null>(null)
  const [failedEvidenceTokenId, setFailedEvidenceTokenId] = useState<number | null>(null)

  useEffect(() => {
    let active = true
    const tokenId = material.tokenId
    if (tokenId === null) return () => { active = false }
    void getOnChainMaterialEvidenceHash(tokenId)
      .then((hash) => {
        if (active) setOnChainEvidence({ tokenId, hash })
      })
      .catch(() => {
        if (active) setFailedEvidenceTokenId(tokenId)
      })
    return () => { active = false }
  }, [material.tokenId])

  const evidenceHash = onChainEvidence?.tokenId === material.tokenId
    ? onChainEvidence.hash
    : null
  const evidenceReadFailed = failedEvidenceTokenId === material.tokenId

  const alreadyVerified = material.verification !== null
  const actionable = user?.id === material.registrantId
  const correctChain = env.besuChainIdHex !== null && chainId === env.besuChainIdHex
  const ready =
    actionable &&
    address !== null &&
    material.tokenId !== null &&
    !alreadyVerified &&
    correctChain &&
    contracts.materialToken !== null &&
    evidenceHash !== null

  const blockedReason = (() => {
    if (material.tokenId === null) return '먼저 소재 이력을 저장해 주세요.'
    if (address === null) return '지갑을 먼저 연결해 주세요.'
    if (env.besuChainIdHex === null) return '이력 확인 설정을 찾을 수 없습니다. 관리자에게 문의해 주세요.'
    if (!correctChain) {
      return '업무용 지갑 연결 환경으로 변경해 주세요.'
    }
    if (contracts.materialToken === null) return '출처 확인 기능을 사용할 수 없습니다. 관리자에게 문의해 주세요.'
    if (evidenceReadFailed) return '등록된 증빙을 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.'
    if (evidenceHash === null) return '등록된 증빙을 확인하는 중입니다.'
    return undefined
  })()

  return (
    <OnChainPanel
      title="출처 확인"
      description="등록된 증빙과 소재 출처가 일치하는지 확인합니다."
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
      actionLabel="출처 확인"
      onSign={() => {
        if (!address || material.tokenId === null || evidenceHash === null) return
        void verify.verify({
          materialId: material.materialId,
          tokenId: material.tokenId,
          evidenceHash,
        })
      }}
    />
  )
}
