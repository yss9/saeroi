import { OnChainPanel } from '../../../components/common/index.ts'
import { contracts } from '../../../config/contracts.ts'
import { env } from '../../../config/env.ts'
import { useAuth } from '../../auth/hooks/useAuth.ts'
import { useEvidence } from '../../evidence/hooks/useEvidence.ts'
import { useWallet } from '../../wallet/hooks/useWallet.ts'
import { useMintMaterialOnChain } from '../hooks/useMintMaterialOnChain.ts'
import { evidencePlaceholder } from '../services/evidencePlaceholder.ts'
import type { MaterialDetail } from '../types/material.types.ts'

/**
 * 등록된 소재를 온체인에 발행한다.
 *
 * **등록자 본인에게만 보인다.** 컨트랙트가 SUPPLIER_ROLE 을 요구하고 서버도 등록자만
 * 받아 적으므로, 남에게 버튼을 보여 주면 지갑 팝업까지 간 뒤 revert 를 본다.
 *
 * 등록 단계에서 올린 저울 사진이 정확히 하나면 그 파일 해시를 사용한다. 새로고침 뒤에도
 * 같은 증빙을 복구해야 하므로 서버의 association을 다시 읽으며, 여러 장이면 임의로 하나를
 * 고르지 않고 사용자가 정리할 때까지 발행을 막는다. 저울 사진이 없는 기존 소재만 소재 코드
 * 기반 자리표시자를 유지한다.
 */
export function MaterialMintPanel({ material }: { material: MaterialDetail }) {
  const { user } = useAuth()
  // **스토어가 아니라 useWallet 을 쓴다.**
  //
  // walletStore 는 useWallet() 을 부르는 컴포넌트가 화면에 떠 있을 때만 채워진다. 그런
  // 컴포넌트는 지갑 화면(WalletButton · WalletRegistrationPanel)에만 있고 이 화면에는
  // 없다. 그래서 **메타마스크에 지갑이 연결돼 있어도 address 가 null 이라 서명 버튼이
  // 영영 잠긴다.** 스토어는 메모리에만 있어 새로고침하면 더 확실히 빈다.
  //
  // useWallet() 은 페이지 로드 때 팝업 없이 기존 승인 계정을 복원하고, 그 과정에서
  // 스토어도 채운다. 2026-09-08 데모에서 이 문제로 막혀 한 번 고쳤던 자리다.
  const wallet = useWallet()
  const address = wallet.address
  const chainId = wallet.chainId
  const mint = useMintMaterialOnChain()
  const evidence = useEvidence('MATERIAL', material.materialId)
  const weightPhotos = evidence.data?.visibility === 'FULL'
    ? (evidence.data.files ?? []).filter((file) => file.evidenceType === 'WEIGHT_PHOTO')
    : []
  const hasAmbiguousWeightPhoto = weightPhotos.length > 1
  const evidenceHash = weightPhotos.length === 1
    ? weightPhotos[0].fileHash
    : evidencePlaceholder(material.materialCode)

  // 등록자만 서명할 수 있다 — 컨트랙트가 SUPPLIER_ROLE 을, 서버가 등록자 본인을 요구한다.
  // 다만 "온체인에 있는가" 는 누구에게나 보여야 하므로 패널 자체는 감추지 않는다.
  const actionable = user?.id === material.registrantId
  const correctChain = env.besuChainIdHex !== null && chainId === env.besuChainIdHex
  const ready =
    actionable &&
    address !== null &&
    material.tokenId === null &&
    correctChain &&
    contracts.materialToken !== null &&
    !evidence.isPending &&
    !evidence.isError &&
    !hasAmbiguousWeightPhoto

  const blockedReason = (() => {
    if (address === null) return '지갑을 먼저 연결해 주세요.'
    if (env.besuChainIdHex === null) return '이력 기록 설정을 확인할 수 없습니다. 관리자에게 문의해 주세요.'
    if (!correctChain) {
      return '업무용 지갑 연결 환경으로 변경해 주세요.'
    }
    if (contracts.materialToken === null) return '이력 기록 기능을 사용할 수 없습니다. 관리자에게 문의해 주세요.'
    if (evidence.isPending) return '등록된 저울 사진 증빙을 확인하는 중입니다.'
    if (evidence.isError || evidence.data?.visibility !== 'FULL') {
      return '저울 사진 증빙을 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.'
    }
    if (hasAmbiguousWeightPhoto) {
      return '저울 사진 증빙이 여러 개입니다. 발행에 사용할 사진을 하나만 남겨 주세요.'
    }
    return undefined
  })()

  return (
    <OnChainPanel
      title="소재 이력 기록"
      description="등록한 소재의 주요 정보를 변경할 수 없는 이력으로 저장합니다."
      onChainId={material.tokenId}
      actionable={actionable}
      ready={ready}
      blockedReason={blockedReason}
      status={mint.status}
      hash={mint.hash}
      blockNumber={mint.blockNumber}
      walletAddress={address}
      chainId={chainId}
      contractAddress={contracts.materialToken}
      error={mint.error}
      isBusy={mint.isBusy}
      isRejected={mint.isRejected}
      actionLabel="소재 이력 저장"
      onSign={() => {
        if (!address) return
        void mint.mint({
          materialId: material.materialId,
          to: address,
          amount: material.totalQuantity,
          rootOrigin: material.rootOrigin,
          metadataUri: `saeroi://material/${material.materialCode}`,
          evidenceHash,
        })
      }}
    />
  )
}
