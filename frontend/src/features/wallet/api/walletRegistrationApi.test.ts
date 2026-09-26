import type { AxiosResponse } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../../services/apiClient.ts'
import {
  getMyWallets,
  registerWallet,
  requestWalletRegistrationNonce,
} from './walletRegistrationApi.ts'

const ADDRESS = '0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045'
const NONCE = '0123456789abcdef0123456789abcdef'
const SIGNATURE = `0x${'ab'.repeat(65)}`

function respondWith<T>(data: T) {
  return vi.spyOn(apiClient, 'post').mockResolvedValue({ data } as AxiosResponse<T>)
}

function respondToGetWith<T>(data: T) {
  return vi.spyOn(apiClient, 'get').mockResolvedValue({ data } as AxiosResponse<T>)
}

afterEach(() => vi.restoreAllMocks())

describe('walletRegistrationApi', () => {
  it('gets the authenticated account wallet history', async () => {
    const wallets = [{
      id: 3,
      walletAddress: ADDRESS,
      chainId: 1337,
      active: true,
      verified: true,
      verifiedAt: '2026-09-07T12:00:00',
      createdAt: '2026-09-07T12:00:00',
      deactivatedAt: null,
    }]
    const get = respondToGetWith(wallets)

    await expect(getMyWallets()).resolves.toEqual(wallets)
    expect(get).toHaveBeenCalledWith('/v1/wallets/me')
  })

  it('requests an authenticated registration nonce with address and chain ID', async () => {
    const post = respondWith({ walletAddress: ADDRESS, nonce: NONCE, message: 'message', expiresAt: 'expiry' })

    await requestWalletRegistrationNonce({ walletAddress: ADDRESS, chainId: 1337 })

    expect(post).toHaveBeenCalledWith('/v1/wallets/nonce', {
      walletAddress: ADDRESS,
      chainId: 1337,
    })
  })

  it('registers the signed wallet using the backend contract', async () => {
    const registered = {
      id: 3,
      walletAddress: ADDRESS,
      chainId: 1337,
      active: true,
      verified: true,
      verifiedAt: '2026-09-07T12:00:00',
      createdAt: '2026-09-07T12:00:00',
      deactivatedAt: null,
    }
    const post = respondWith(registered)

    await expect(registerWallet({
      walletAddress: ADDRESS,
      chainId: 1337,
      nonce: NONCE,
      signature: SIGNATURE,
    })).resolves.toEqual(registered)

    expect(post).toHaveBeenCalledWith('/v1/wallets', {
      walletAddress: ADDRESS,
      chainId: 1337,
      nonce: NONCE,
      signature: SIGNATURE,
    })
  })
})
