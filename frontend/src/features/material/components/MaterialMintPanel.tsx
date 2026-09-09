import { OnChainPanel } from '../../../components/common/index.ts'
import { contracts } from '../../../config/contracts.ts'
import { env } from '../../../config/env.ts'
import { useAuth } from '../../auth/hooks/useAuth.ts'
import { useWallet } from '../../wallet/hooks/useWallet.ts'
import { useMintMaterialOnChain } from '../hooks/useMintMaterialOnChain.ts'
import type { MaterialDetail } from '../types/material.types.ts'

/**
 * 등록된 소재를 온체인에 발행한다.
 *
 * **등록자 본인에게만 보인다.** 컨트랙트가 SUPPLIER_ROLE 을 요구하고 서버도 등록자만
 * 받아 적으므로, 남에게 버튼을 보여 주면 지갑 팝업까지 간 뒤 revert 를 본다.
 *
 * `metadataUri` 와 `evidenceHash` 는 지금 자리표시자다. 정책상 Evidence Manifest 의
 * SHA-256 이 들어가야 하는데 그 매니페스트를 만드는 경로가 아직 없다. 컨트랙트가
 * `evidenceHash != 0` 만 요구하므로 소재 코드에서 파생한 값을 넣어 발행은 되게 하되,
 * **이 값이 증빙과 대조되지는 않는다** — 매니페스트가 붙으면 여기만 바꾼다.
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

  // 등록자만 서명할 수 있다 — 컨트랙트가 SUPPLIER_ROLE 을, 서버가 등록자 본인을 요구한다.
  // 다만 "온체인에 있는가" 는 누구에게나 보여야 하므로 패널 자체는 감추지 않는다.
  const actionable = user?.id === material.registrantId
  const correctChain = env.besuChainIdHex !== null && chainId === env.besuChainIdHex
  const ready =
    actionable &&
    address !== null &&
    material.tokenId === null &&
    correctChain &&
    contracts.materialToken !== null

  const blockedReason = (() => {
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
      title="소재 발행"
      description="이 소재를 블록체인에 발행합니다. 발행해야 온체인 판매 등록을 할 수 있습니다."
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
      actionLabel="온체인 발행"
      onSign={() => {
        if (!address) return
        void mint.mint({
          materialId: material.materialId,
          to: address,
          amount: material.totalQuantity,
          rootOrigin: material.rootOrigin,
          metadataUri: `saeroi://material/${material.materialCode}`,
          evidenceHash: evidencePlaceholder(material.materialCode),
        })
      }}
    />
  )
}

/**
 * 컨트랙트가 0 인 evidenceHash 를 거절하므로 자리를 채운다.
 * 소재 코드에서 파생해 같은 소재는 같은 값이 나오게 한다 — 무작위로 두면 재시도할 때마다
 * 값이 달라져 나중에 매니페스트와 대조할 때 어느 것이 맞는지 알 수 없다.
 */
function evidencePlaceholder(materialCode: string): string {
  let hash = 0n
  for (const char of materialCode) {
    hash = (hash * 31n + BigInt(char.codePointAt(0) ?? 0)) % (2n ** 256n)
  }
  return `0x${hash.toString(16).padStart(64, '0')}`
}
