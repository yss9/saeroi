// ABI 계약 검증.
//
// ABI 파일은 blockchain 저장소의 artifact 에서 자동 생성된다(`npm run gen:abi`).
// 따라서 "문법이 맞는가" 는 더 이상 검증할 의미가 없다. 대신 **재생성 이후에도
// 우리가 부르는 함수가 그대로 있는가** 를 지킨다.
//
// 컨트랙트가 재배포되면서 함수가 사라지거나 인자가 바뀌면 여기서 걸린다.
// 이 방어가 없으면 ABI 를 다시 생성한 순간 조용히 깨지고, 실제 호출 시점에야 드러난다.
//
// 실제로 그런 일이 있었다 — 손으로 옮겨 적을 때 `verifyProcessing` 을 MaterialToken 에
// 넣어두었는데, 그 기능은 ProcessingRegistry 로 분리되어 있었다.
//
// ⚠️ 이 테스트가 증명하지 못하는 것: **배포된 컨트랙트와 일치하는가.**
// artifact 와는 일치하지만, 그 artifact 가 실제 체인에 올라간 것과 같은지는
// 주소가 나온 뒤 통합 확인이 따로 필요하다.

import { Interface } from 'ethers'
import { describe, expect, it } from 'vitest'
import { abis, type ContractName } from '../contracts.ts'

/** 서비스 계층이 실제로 호출하는 함수와 인자 개수. */
const REQUIRED: Record<ContractName, ReadonlyArray<readonly [string, number]>> = {
  materialToken: [
    ['mintMaterial', 5],
    ['burnMaterial', 4],
    ['safeTransferFrom', 5], // ERC-1155 는 from 포함 5개다 (ERC-721 과 다름)
    ['balanceOf', 2],
    ['availableBalance', 2],
    ['lockedBalanceOf', 2],
    ['verifyEvidence', 2],
  ],
  marketplace: [
    ['createListing', 3],
    ['cancelListing', 1],
    ['createOrder', 2],
    ['cancelOrder', 1],
    ['markShipped', 1],
    ['confirmReceipt', 1],
    ['getListing', 1],
    ['getOrder', 1],
    ['calculateTotalPrice', 2],
  ],
  // 가공·생산 서비스는 각 도메인 담당이 만든다. 여기서는 ABI 가 실려 있는지만 확인한다.
  processingRegistry: [],
  productionRegistry: [],
}

describe.each(Object.keys(abis) as ContractName[])('%s ABI', (name) => {
  const iface = new Interface(abis[name])

  it('시그니처가 실려 있다', () => {
    expect(iface.fragments.length).toBeGreaterThan(0)
  })

  const required = REQUIRED[name]
  if (required.length > 0) {
    it.each(required)('%s 은 인자 %i개', (fn, argCount) => {
      const fragment = iface.getFunction(fn)
      expect(fragment, `${fn} 이 ${name} ABI 에 없다`).not.toBeNull()
      expect(fragment!.inputs).toHaveLength(argCount)
    })
  }
})

describe('MaterialToken 이벤트', () => {
  const iface = new Interface(abis.materialToken)

  // mintMaterial 의 tokenId 는 반환값이라 트랜잭션에서 읽을 수 없다.
  // 이 이벤트가 없으면 발행된 소재의 id 를 알아낼 방법이 사라진다.
  it('MaterialMinted 에서 tokenId 를 꺼낼 수 있다', () => {
    const event = iface.getEvent('MaterialMinted')
    expect(event).not.toBeNull()
    expect(event!.inputs.map((input) => input.name)).toContain('tokenId')
  })

  it('rootOrigin 은 uint8 로 넘어간다', () => {
    const rootOrigin = iface
      .getFunction('mintMaterial')!
      .inputs.find((input) => input.name === 'rootOrigin')
    expect(rootOrigin?.type).toBe('uint8')
  })

  // 가공 검증은 MaterialToken 이 아니라 ProcessingRegistry 담당이다.
  // 여기 다시 생기면 어느 쪽을 써야 하는지 정하고 이 테스트를 지워야 한다.
  it('verifyProcessing 은 MaterialToken 에 없다', () => {
    expect(iface.getFunction('verifyProcessing')).toBeNull()
  })
})

describe('Marketplace 분쟁', () => {
  const iface = new Interface(abis.marketplace)

  // 정책 09.3: ADMIN 판정을 폐지하고 **양측 서명** 으로 확정한다.
  // getDispute 가 양쪽 선택을 따로 들고 있어야 그 정책이 성립한다.
  it('양측 선택을 각각 기록한다', () => {
    const tuple = iface.getFunction('getDispute')!.outputs[0]
    const fields = tuple.components?.map((component) => component.name) ?? []
    expect(fields).toContain('sellerChoice')
    expect(fields).toContain('buyerChoice')
  })
})

describe('결제는 서버가 기록한다', () => {
  // markPaid 는 ABI 에는 있지만 PAYMENT_MANAGER_ROLE 이 필요한 서버 전용이다.
  // 프론트 서비스가 이걸 export 하면 화면에서 부르게 되고, 권한 오류로만 막히는 건 늦다.
  it('marketplace 서비스가 markPaid 를 노출하지 않는다', async () => {
    const service = await import('../../features/marketplace/services/marketplaceContractService.ts')
    expect(Object.keys(service)).not.toContain('markPaid')
  })
})
