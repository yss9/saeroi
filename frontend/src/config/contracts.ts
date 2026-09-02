// Smart Contract 주소·ABI 설정.
//
// 주소를 Component 에 하드코딩하지 않는다. 컨트랙트가 재배포되면 주소가 바뀌고,
// 하드코딩된 주소는 조용히 잘못된 체인을 가리키게 된다.
//
// ABI 와 Contract Address 의 Source of Truth 는 `blockchain/master` 브랜치다.
// 여기서는 배포 산출물을 받아 env 로 주입받기만 한다. 복제해서 관리하지 않는다.

import { isAddress } from 'ethers'

/** 주소 형식이 아니면 null. 잘못된 설정이 런타임까지 흘러가지 않게 여기서 거른다. */
function contractAddress(value: string | undefined): string | null {
  const trimmed = value?.trim()
  if (!trimmed) return null
  return isAddress(trimmed) ? trimmed : null
}

export const contracts = {
  materialToken: contractAddress(import.meta.env.VITE_MATERIAL_CONTRACT_ADDRESS),
  marketplace: contractAddress(import.meta.env.VITE_MARKETPLACE_CONTRACT_ADDRESS),
  escrow: contractAddress(import.meta.env.VITE_ESCROW_CONTRACT_ADDRESS),
} as const

export type ContractName = keyof typeof contracts

/**
 * ABI 저장소.
 *
 * TODO(blockchain 파트): `blockchain/master` 의 `abi/` 산출물이 나오면
 * 그 JSON 을 `src/config/abi/` 로 받아 아래에 연결한다. 지금은 빈 배열이라
 * getContract() 를 호출해도 실제 메서드가 없다 — 구조만 잡아둔 상태다.
 */
export const abis: Record<ContractName, readonly unknown[]> = {
  materialToken: [],
  marketplace: [],
  escrow: [],
}

/** 해당 컨트랙트를 호출할 준비가 됐는지. UI 가 기능을 비활성화할 때 쓴다. */
export function isContractConfigured(name: ContractName): boolean {
  return contracts[name] !== null && abis[name].length > 0
}
