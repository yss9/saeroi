import { describe, expect, it } from 'vitest'
import { adjacentScrollStop } from './aboutScroll.ts'

describe('ABOUT content scroll stops', () => {
  const stops = [0, 800, 1200, 1600, 2400]

  it('moves one content stop down or up, including connection phases', () => {
    expect(adjacentScrollStop(stops, 800, 1)).toBe(1200)
    expect(adjacentScrollStop(stops, 1600, -1)).toBe(1200)
    expect(adjacentScrollStop(stops, 805, -1)).toBe(0)
  })

  it('does not wrap at page boundaries', () => {
    expect(adjacentScrollStop(stops, 0, -1)).toBeUndefined()
    expect(adjacentScrollStop(stops, 2400, 1)).toBeUndefined()
  })

})
