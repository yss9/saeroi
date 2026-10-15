// Smart Contract 주소·ABI 설정.
//
// 주소를 Component 에 하드코딩하지 않는다. 컨트랙트가 재배포되면 주소가 바뀌고,
// 하드코딩된 주소는 조용히 잘못된 체인을 가리키게 된다.
//
// ABI 와 Contract Address 의 Source of Truth 는 `blockchain/master` 브랜치다.
// ABI 파일은 그 저장소의 artifact 에서 **생성**한다 (`npm run gen:abi`).
// 손으로 옮겨 적지 않는다 — 실제로 틀린 적이 있다.

import { isAddress, type InterfaceAbi } from 'ethers'
import { MARKETPLACE_ABI } from './abi/marketplace.abi.ts'
import { MATERIAL_TOKEN_ABI } from './abi/materialToken.abi.ts'
import { PROCESSING_REGISTRY_ABI } from './abi/processingRegistry.abi.ts'
import { PRODUCTION_REGISTRY_ABI } from './abi/productionRegistry.abi.ts'
import { PREDICTION_POINT_ABI } from './abi/predictionPoint.abi.ts'
import { PREDICTION_MARKET_ABI } from './abi/predictionMarket.abi.ts'

/** 주소 형식이 아니면 null. 잘못된 설정이 런타임까지 흘러가지 않게 여기서 거른다. */
function contractAddress(value: string | undefined): string | null {
  const trimmed = value?.trim()
  if (!trimmed) return null
  return isAddress(trimmed) ? trimmed : null
}

/**
 * 기본 공급망 컨트랙트 네 개와 예측시장 컨트랙트 두 개를 관리한다.
 *
 * | 컨트랙트 | 담당 도메인 |
 * | --- | --- |
 * | MaterialToken      | 소재 발행·소각·Lock·이전 (ERC-1155) |
 * | Marketplace        | 판매 등록·주문·배송·수령·분쟁 |
 * | ProcessingRegistry | 가공 작업 생성·결과 제출·승인/반려 |
 * | ProductionRegistry | 생산 배치·투입 Lock·확정·제품 연결 |
 *
 * ❌ Escrow 는 없다 — 정책 v3에서 **대금 예치를 전면 폐지**했다(08절).
 * 결제는 카카오페이 PG 로 오프체인 처리하고, 배송 중 재판매는 Escrow Lock 이 아니라
 * MARKETPLACE Lock 을 유지하는 방식으로 막는다(06.3절).
 */
export const contracts = {
  materialToken: contractAddress(import.meta.env.VITE_MATERIAL_CONTRACT_ADDRESS),
  marketplace: contractAddress(import.meta.env.VITE_MARKETPLACE_CONTRACT_ADDRESS),
  processingRegistry: contractAddress(import.meta.env.VITE_PROCESSING_CONTRACT_ADDRESS),
  productionRegistry: contractAddress(import.meta.env.VITE_PRODUCTION_CONTRACT_ADDRESS),
  predictionPoint: contractAddress(import.meta.env.VITE_PREDICTION_POINT_CONTRACT_ADDRESS),
  predictionMarket: contractAddress(import.meta.env.VITE_PREDICTION_MARKET_CONTRACT_ADDRESS),
} as const

export type ContractName = keyof typeof contracts

export const abis: Record<ContractName, InterfaceAbi> = {
  materialToken: MATERIAL_TOKEN_ABI as unknown as InterfaceAbi,
  marketplace: MARKETPLACE_ABI as unknown as InterfaceAbi,
  processingRegistry: PROCESSING_REGISTRY_ABI as unknown as InterfaceAbi,
  productionRegistry: PRODUCTION_REGISTRY_ABI as unknown as InterfaceAbi,
  predictionPoint: PREDICTION_POINT_ABI as unknown as InterfaceAbi,
  predictionMarket: PREDICTION_MARKET_ABI as unknown as InterfaceAbi,
}

/**
 * 해당 컨트랙트를 호출할 준비가 됐는지.
 *
 * ABI 는 코드에 들어 있으므로 실제로는 **주소 설정 여부**만 본다.
 * 화면은 이 값으로 온체인 기능 버튼을 잠근다 — 주소가 없는데 버튼을 눌러
 * 에러를 보게 하는 것보다 애초에 비활성화하는 편이 낫다.
 */
export function isContractConfigured(name: ContractName): boolean {
  return contracts[name] !== null
}
