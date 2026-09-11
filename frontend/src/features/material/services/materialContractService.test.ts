import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * <b>중복 발행 가드가 체인 전체를 훑지 않는다.</b>
 *
 * {@code findMintedMaterial} 은 서명 팝업을 띄우기 <b>전에</b> 돈다 — 체인이 정본이므로
 * 이미 발행돼 있으면 다시 서명하지 않기 위해서다. 그래서 이 조회가 느리면 그 시간이
 * 통째로 MetaMask 팝업 앞을 막고, 사용자는 아무 반응 없는 화면을 본다.
 *
 * 처음에는 {@code fromBlock} 이 0 이었다. 배포 직후(2026-09-08)에는 블록이 얼마 없어
 * 빨랐지만 SSAFY 체인은 계속 쌓인다 — 사흘 만에 1,122만 블록이 됐고 전 구간
 * {@code eth_getLogs} 가 <b>33초</b>가 됐다. 같은 질의가 배포 블록부터면 <b>455ms</b> 다
 * (2026-09-11 실측).
 *
 * 컨트랙트보다 앞선 블록에는 그 컨트랙트의 로그가 있을 수 없으므로 건너뛰어도 잃는 것이
 * 없다. <b>이 검사가 없으면 다시 0 으로 돌아가도 아무 테스트도 깨지지 않는다</b> —
 * 결과는 여전히 맞고 화면에도 오류가 없이, 그저 느려지기만 하기 때문이다.
 */
const queryFilter = vi.fn()
const filters = { MaterialMinted: vi.fn(() => 'filter') }

vi.mock('../../../services/blockchain/contractFactory.ts', () => ({
  getReadContract: () => ({ queryFilter, filters }),
  getWriteContract: vi.fn(),
  readEventArg: vi.fn(),
}))

vi.mock('../../../config/env.ts', () => ({
  env: { chainFromBlock: 11_221_171 },
}))

beforeEach(() => {
  queryFilter.mockResolvedValue([])
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('findMintedMaterial — 조회 범위', () => {
  it('제네시스가 아니라 설정된 배포 블록부터 조회한다', async () => {
    const { findMintedMaterial } = await import('./materialContractService.ts')

    await findMintedMaterial('0xregistrar', 'saeroi://material/MAT-000014')

    expect(queryFilter).toHaveBeenCalledWith('filter', 11_221_171, 'latest')
  })

  it('서명한 지갑으로 먼저 좁힌다', async () => {
    // metadataUri 는 인덱싱되지 않은 값이라 필터로 좁힐 수 없다. 인덱싱된 operator
    // (= 서명한 지갑)로 줄이지 않으면 남의 발행까지 전부 받아 온다.
    const { findMintedMaterial } = await import('./materialContractService.ts')

    await findMintedMaterial('0xregistrar', 'saeroi://material/MAT-000014')

    expect(filters.MaterialMinted).toHaveBeenCalledWith('0xregistrar', null, null)
  })

  it('metadataUri 가 같은 가장 오래된 발행을 고른다', async () => {
    // 이 가드가 없던 시절에 같은 소재가 두 번 발행된 적이 있다. 뒤의 것을 고르면
    // 앞의 토큰이 영원히 가리켜지지 않는다.
    const { findMintedMaterial } = await import('./materialContractService.ts')
    queryFilter.mockResolvedValue([
      { args: { metadataUri: 'other', tokenId: 1n }, transactionHash: '0xa', blockNumber: 10 },
      { args: { metadataUri: 'saeroi://x', tokenId: 2n }, transactionHash: '0xb', blockNumber: 20 },
      { args: { metadataUri: 'saeroi://x', tokenId: 9n }, transactionHash: '0xc', blockNumber: 30 },
    ])

    const found = await findMintedMaterial('0xregistrar', 'saeroi://x')

    expect(found).toEqual({ onChainId: 2, txHash: '0xb', blockNumber: 20 })
  })

  it('조회가 실패해도 던지지 않는다', async () => {
    // RPC 가 흔들릴 때 발행을 막으면 정상 사용자가 아무것도 못 한다. 그때는 이 가드가
    // 없던 예전 상태로 돌아갈 뿐이다.
    const { findMintedMaterial } = await import('./materialContractService.ts')
    queryFilter.mockRejectedValue(new Error('RPC timeout'))

    await expect(findMintedMaterial('0xregistrar', 'saeroi://x')).resolves.toBeNull()
  })
})
