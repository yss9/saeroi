// Smart Contract 주소·ABI 설정.
//
// 주소를 Component 에 하드코딩하지 않는다. 컨트랙트가 재배포되면 주소가 바뀌고,
// 하드코딩된 주소는 조용히 잘못된 체인을 가리키게 된다.
//
// ABI 와 Contract Address 의 Source of Truth 는 `blockchain/master` 브랜치다.
// 여기서는 배포 산출물을 받아 env 로 주입받기만 한다. 복제해서 관리하지 않는다.

import { isAddress, type InterfaceAbi } from 'ethers'
import { MARKETPLACE_ABI } from './abi/marketplace.abi.ts'
import { MATERIAL_TOKEN_ABI } from './abi/materialToken.abi.ts'

/** 주소 형식이 아니면 null. 잘못된 설정이 런타임까지 흘러가지 않게 여기서 거른다. */
function contractAddress(value: string | undefined): string | null {
  const trimmed = value?.trim()
  if (!trimmed) return null
  return isAddress(trimmed) ? trimmed : null
}

/**
 * 배포된 컨트랙트는 두 개다.
 *
 * ❌ Escrow 는 없다 — 정책 v3에서 **대금 예치를 전면 폐지**했다(08절).
 * 결제는 카카오페이 PG 로 오프체인 처리하고, 배송 중 재판매는 Escrow Lock 이 아니라
 * MARKETPLACE Lock 을 유지하는 방식으로 막는다(06.3절).
 */
export const contracts = {
  materialToken: contractAddress(import.meta.env.VITE_MATERIAL_CONTRACT_ADDRESS),
  marketplace: contractAddress(import.meta.env.VITE_MARKETPLACE_CONTRACT_ADDRESS),
} as const

export type ContractName = keyof typeof contracts

export const abis: Record<ContractName, InterfaceAbi> = {
  materialToken: MATERIAL_TOKEN_ABI as unknown as InterfaceAbi,
  marketplace: MARKETPLACE_ABI as unknown as InterfaceAbi,
}

/**
 * 해당 컨트랙트를 호출할 준비가 됐는지.
 *
 * ABI 는 이제 코드에 들어 있으므로 실제로는 **주소 설정 여부**만 본다.
 * 화면은 이 값으로 온체인 기능 버튼을 잠근다 — 주소가 없는데 버튼을 눌러
 * 에러를 보게 하는 것보다 애초에 비활성화하는 편이 낫다.
 */
export function isContractConfigured(name: ContractName): boolean {
  return contracts[name] !== null
}
