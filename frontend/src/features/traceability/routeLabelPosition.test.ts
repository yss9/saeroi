import { describe, expect, it } from 'vitest'
import { routeLabelPosition } from './routeLabelPosition.ts'

describe('route label position', () => {
  it.each([false, true])('keeps labels within the viewport (mapView=%s)', (compact) => {
    const left = compact ? 315 : 0
    const right = compact ? 840 : 1000
    for (const origin of [{ x: left + 40, y: 20 }, { x: right - 40, y: 600 }]) {
      const point = routeLabelPosition(origin, [], compact)!
      expect(point.x - 37).toBeGreaterThanOrEqual(left)
      expect(point.x + 37).toBeLessThanOrEqual(right)
      expect(point.y - 12).toBeGreaterThanOrEqual(0)
      expect(point.y + 12).toBeLessThanOrEqual(620)
    }
  })

  it('moves the label away from nearby marker halos', () => {
    const origin = { x: 400, y: 200 }
    expect(routeLabelPosition(origin, [origin, { x: 430, y: 240 }], false)).toBeNull()
    expect(routeLabelPosition({ x: -100, y: 100 }, [], true)).toBeNull()
  })
})
