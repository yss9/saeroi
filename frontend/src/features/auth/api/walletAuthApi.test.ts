// 경로와 요청 바디를 문자열로 못 박는다.
//
// Backend 와 확정한 계약(C1: /v1 경로, C2: verify 에 nonce 포함)이 조용히 바뀌는 것을
// 막는 것이 목적이다. MSW 가 없으므로 apiClient.post 를 spy 로 갈아 끼운다 —
// 그러면 인터셉터는 돌지 않지만, 봉투 해제는 apiClient 의 책임이고 이미 다른 곳에서
// 신뢰하고 있으므로 여기서 검증할 대상이 아니다.

import type { AxiosResponse } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../../services/apiClient.ts'
import { requestWalletNonce, verifyWalletSignature } from './walletAuthApi.ts'

const ADDRESS = '0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045'
const NONCE = '0123456789abcdef0123456789abcdef'
const SIGNATURE = '0x' + 'ab'.repeat(65)

function respondWith<T>(data: T) {
  return vi.spyOn(apiClient, 'post').mockResolvedValue({ data } as AxiosResponse<T>)
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('requestWalletNonce', () => {
  it('버전이 붙은 경로로 주소와 10진수 체인 ID를 보낸다', async () => {
    const post = respondWith({ walletAddress: ADDRESS, nonce: NONCE, message: 'm', expiresAt: 'e' })

    await requestWalletNonce({ walletAddress: ADDRESS, chainId: 1337 })

    expect(post).toHaveBeenCalledWith('/v1/auth/wallet/nonce', {
      walletAddress: ADDRESS,
      chainId: 1337,
    })
  })

  it('봉투가 벗겨진 알맹이를 그대로 돌려준다', async () => {
    const payload = { walletAddress: ADDRESS, nonce: NONCE, message: 'm', expiresAt: 'e' }
    respondWith(payload)

    await expect(requestWalletNonce({ walletAddress: ADDRESS, chainId: 1337 })).resolves.toEqual(
      payload,
    )
  })
})

describe('verifyWalletSignature', () => {
  it('nonce 를 포함해 보낸다', async () => {
    const post = respondWith({ accessToken: 'jwt', user: {} })

    await verifyWalletSignature({ walletAddress: ADDRESS, nonce: NONCE, signature: SIGNATURE })

    expect(post).toHaveBeenCalledWith('/v1/auth/wallet/verify', {
      walletAddress: ADDRESS,
      nonce: NONCE,
      signature: SIGNATURE,
    })
  })

  it('accessToken 과 user 를 돌려준다', async () => {
    const payload = { accessToken: 'jwt', user: { id: 1 } }
    respondWith(payload)

    await expect(
      verifyWalletSignature({ walletAddress: ADDRESS, nonce: NONCE, signature: SIGNATURE }),
    ).resolves.toEqual(payload)
  })
})
