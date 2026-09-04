// ABI 시그니처 검증.
//
// ABI 를 손으로 옮겨 적었기 때문에 오타가 있어도 타입 검사·빌드는 전부 통과한다.
// 잘못된 시그니처는 **실제 호출 시점** 에야 드러나는데, 그때는 이미 지갑 팝업이 뜬 뒤다.
//
// 여기서 잡는 것
//   1) ABI 문자열이 ethers 문법에 맞는가        → Interface 생성이 던지지 않는가
//   2) 우리가 부르는 함수가 실제로 정의돼 있는가 → getFunction 이 찾는가
//   3) 인자 개수가 맞는가                        → 개수가 틀리면 호출이 revert 된다
//
// ⚠️ 이 테스트가 증명하지 못하는 것: **배포된 컨트랙트와 일치하는가.**
// 그건 실제 체인에 붙여봐야 안다. 주소가 나오면 통합 확인이 따로 필요하다.

import { Interface } from 'ethers'
import { describe, expect, it } from 'vitest'
import { MARKETPLACE_ABI } from './marketplace.abi.ts'
import { MATERIAL_TOKEN_ABI } from './materialToken.abi.ts'

describe('MaterialToken ABI', () => {
  const iface = new Interface(MATERIAL_TOKEN_ABI)

  it('문법 오류 없이 파싱된다', () => {
    expect(iface.fragments.length).toBeGreaterThan(0)
  })

  // 서비스 계층이 실제로 부르는 함수들. 이름이나 인자 수가 틀리면 여기서 걸린다.
  it.each([
    ['mintMaterial', 5],
    ['burnMaterial', 4],
    ['safeTransferFrom', 5], // ERC-1155 는 from 을 포함해 5개다 (ERC-721 과 다름)
    ['balanceOf', 2],
    ['availableBalance', 2],
    ['lockedBalanceOf', 2],
    ['createMaterialLock', 5],
    ['releaseMaterialLock', 2],
    ['consumeMaterialLock', 3],
    ['verifyEvidence', 2],
    ['verifySource', 2],
    ['verifyTransaction', 3],
    ['verifyProcessing', 3],
  ])('%s 은 인자 %i개', (name, argCount) => {
    const fn = iface.getFunction(name)
    expect(fn, `${name} 이 ABI 에 없다`).not.toBeNull()
    expect(fn!.inputs).toHaveLength(argCount)
  })

  // mintMaterial 의 tokenId 는 반환값이라 트랜잭션에서 읽을 수 없다.
  // 이 이벤트가 없으면 발행된 소재의 id 를 알아낼 방법이 사라진다.
  it('MaterialMinted 이벤트에서 tokenId 를 꺼낼 수 있다', () => {
    const event = iface.getEvent('MaterialMinted')
    expect(event).not.toBeNull()
    expect(event!.inputs.map((input) => input.name)).toContain('tokenId')
  })

  it('rootOrigin 은 uint8 로 넘어간다', () => {
    const mint = iface.getFunction('mintMaterial')!
    const rootOrigin = mint.inputs.find((input) => input.name === 'rootOrigin')
    expect(rootOrigin?.type).toBe('uint8')
  })
})

describe('Marketplace ABI', () => {
  const iface = new Interface(MARKETPLACE_ABI)

  it('문법 오류 없이 파싱된다', () => {
    expect(iface.fragments.length).toBeGreaterThan(0)
  })

  it.each([
    ['createListing', 3],
    ['cancelListing', 1],
    ['createOrder', 2],
    ['cancelOrder', 1],
    ['markShipped', 1],
    ['confirmReceipt', 1],
    ['getListing', 1],
    ['getOrder', 1],
    ['calculateTotalPrice', 2],
  ])('%s 은 인자 %i개', (name, argCount) => {
    const fn = iface.getFunction(name)
    expect(fn, `${name} 이 ABI 에 없다`).not.toBeNull()
    expect(fn!.inputs).toHaveLength(argCount)
  })

  // 결제는 오프체인이고 markPaid 는 PAYMENT_MANAGER_ROLE 이 필요한 서버 전용이다.
  // 프론트 ABI 에 넣어두면 누군가 호출하게 되고, 권한 오류로만 막히는 건 늦다.
  it('markPaid 는 프론트 ABI 에 없다', () => {
    // ethers v6 의 getFunction 은 못 찾으면 null 을 준다 (예외가 아니다).
    expect(iface.getFunction('markPaid')).toBeNull()
  })

  it('getOrder 는 결제 참조를 해시로만 돌려준다', () => {
    const fn = iface.getFunction('getOrder')!
    const tuple = fn.outputs[0]
    const field = tuple.components?.find((c) => c.name === 'paymentReferenceHash')
    expect(field?.type).toBe('bytes32')
  })
})
