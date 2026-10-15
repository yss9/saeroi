import type { AxiosResponse } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../../services/apiClient.ts'
import { processingJobApi } from './processingJobApi.ts'

const JOB_ID = 17
const SIGNATURE = `0x${'ab'.repeat(65)}`

afterEach(() => vi.restoreAllMocks())

describe('processingJobApi signature flow', () => {
  it('작업과 서명 동작을 묶은 챌린지를 요청한다', async () => {
    const challenge = {
      challengeId: 3,
      action: 'REJECT' as const,
      walletAddress: '0x0000000000000000000000000000000000000001',
      nonce: '0123456789abcdef0123456789abcdef',
      message: 'sign me',
      expiresAt: '2026-09-11T12:05:00',
    }
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: challenge } as AxiosResponse)

    await expect(processingJobApi.challenge(JOB_ID, 'REJECT', '수량 재확인')).resolves.toEqual(challenge)
    expect(post).toHaveBeenCalledWith(`/processing-jobs/${JOB_ID}/signature-challenge`, {
      action: 'REJECT',
      rejectionReason: '수량 재확인',
    })
  })

  it('결과 제출에는 챌린지 ID와 MetaMask 서명을 함께 보낸다', async () => {
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: {} } as AxiosResponse)

    await processingJobApi.submitResult(JOB_ID, 3, SIGNATURE)

    expect(post).toHaveBeenCalledWith(`/processing-jobs/${JOB_ID}/results/submit`, {
      challengeId: 3,
      signature: SIGNATURE,
    })
  })

  it('반려 사유도 서명 검증 요청과 동일하게 전송한다', async () => {
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: {} } as AxiosResponse)

    await processingJobApi.reject(JOB_ID, 4, SIGNATURE, 'Loss 증빙 보완')

    expect(post).toHaveBeenCalledWith(`/processing-jobs/${JOB_ID}/reject`, {
      challengeId: 4,
      signature: SIGNATURE,
      reason: 'Loss 증빙 보완',
    })
  })
})
