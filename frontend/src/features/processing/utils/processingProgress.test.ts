// 새 순서에서 **결제 다음 차례는 브랜드다.** 결제가 끝난 작업(PAID)은 브랜드가 소재를
// 온체인 가공 작업에 잠가 줘야 가공사가 수락(=온체인 서명)할 수 있다. 목록이 PAID 를
// "기다리면 되는 건" 으로 묶으면 양쪽이 서로를 기다리다 기한이 지나 자동 거절된다.
//
// 끝난 작업도 마찬가지다. 거절·만료·취소로 끝났어도 온체인 잠금이 남아 있으면
// (`chainReleaseRequired`) 브랜드 지갑의 서명이 한 번 더 남아 있다 — "종료" 로 접으면
// 그 수량은 영영 묶인 채로 남는다.

import { describe, expect, it } from 'vitest'
import { jobLabel, phaseOf, progressLabel } from './processingProgress.ts'

describe('브랜드 목록의 묶음', () => {
  it('결제가 끝난 작업은 브랜드 차례다 — 온체인 등록이 남아 있다', () => {
    expect(phaseOf('ACCEPTED', 'PAID')).toBe('MY_TURN')
  })

  it('PAID 라벨은 브랜드에게 기다리라고 말하지 않는다', () => {
    expect(jobLabel('PAID')).not.toBe('수락 대기')
    expect(jobLabel('PAID')).toMatch(/온체인/)
  })

  /*
    **등록이 끝나면 PAID 는 더 이상 내 차례가 아니다.** 그 뒤로 기다리는 것은 가공사의
    온체인 서명이고, 그 창은 사흘이다 — 목록이 사흘 내내 "내 차례 · 온체인 등록 대기"
    라고 말하는 동안 작업 상세는 "가공업체의 수락을 기다리고 있습니다" 라고 말한다.
    F2 가 고친 것과 같은 어긋남이 한 걸음 뒤로 밀린 것뿐이다.
  */
  it('등록이 끝난 PAID 는 내 차례가 아니라 진행 중이다', () => {
    expect(phaseOf('ACCEPTED', 'PAID', { chainRegistered: true })).toBe('IN_PROGRESS')
  })

  it('등록이 끝난 PAID 라벨은 가공사의 수락을 기다린다고 말한다', () => {
    expect(jobLabel('PAID', true)).toBe('수락 대기')
    expect(progressLabel('ACCEPTED', 'PAID', true)).toBe('수락 대기')
    expect(progressLabel('ACCEPTED', 'PAID')).toBe('온체인 등록 대기')
  })

  it.each(['DECLINED', 'EXPIRED', 'CANCELED'] as const)(
    '%s 라도 온체인 잠금이 남아 있으면 브랜드 차례다', (status) => {
      expect(phaseOf('ACCEPTED', status, { chainReleaseRequired: true })).toBe('MY_TURN')
    })

  it.each(['DECLINED', 'EXPIRED', 'CANCELED'] as const)(
    '%s 이고 잠금도 남지 않았으면 종료다', (status) => {
      expect(phaseOf('ACCEPTED', status, { chainReleaseRequired: false })).toBe('CLOSED')
    })

  it('결과 반려(REJECTED)는 잠금과 무관하게 여전히 종료 묶음이다', () => {
    expect(phaseOf('ACCEPTED', 'REJECTED')).toBe('CLOSED')
  })

  it('기존 묶음은 그대로다', () => {
    expect(phaseOf('ACCEPTED', 'CREATED')).toBe('MY_TURN')
    expect(phaseOf('ACCEPTED', 'ACCEPTED')).toBe('MY_TURN')
    expect(phaseOf('ACCEPTED', 'RETURN_SHIPPED')).toBe('MY_TURN')
    expect(phaseOf('ACCEPTED', 'SHIPPED')).toBe('IN_PROGRESS')
    expect(phaseOf('ACCEPTED', 'CONFIRMED')).toBe('DONE')
  })
})
