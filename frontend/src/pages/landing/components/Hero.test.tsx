import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Hero } from './Hero.tsx'

const animation = vi.hoisted(() => ({
  set: vi.fn(), to: vi.fn(), fromTo: vi.fn(), call: vi.fn(), timeline: vi.fn(), revert: vi.fn(),
}))
vi.mock('gsap', () => ({ gsap: {
  set: animation.set,
  timeline: animation.timeline,
  context: (callback: () => void) => { callback(); return { revert: animation.revert } },
} }))

beforeEach(() => {
  vi.clearAllMocks()
  const timeline = { to: animation.to, fromTo: animation.fromTo, call: animation.call }
  animation.timeline.mockReturnValue(timeline)
  animation.to.mockReturnValue(timeline)
  animation.fromTo.mockReturnValue(timeline)
  animation.call.mockReturnValue(timeline)
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false })))
  Object.defineProperty(SVGElement.prototype, 'getBBox', { configurable: true, value: () => ({ x: -560, y: -35, width: 375, height: 380 }) })
})
afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  Reflect.deleteProperty(SVGElement.prototype, 'getBBox')
})

describe('HERO logo intro', () => {
  it('uses one overlapping timeline with asymmetric deformation and three phases per letter', () => {
    const { container } = render(<Hero onLogoReveal={vi.fn()} />)
    expect(animation.timeline).toHaveBeenCalledTimes(1)
    const starts: number[] = []
    for (const id of ['s', 'a', 'e', 'r', 'o', 'i']) {
      const letter = container.querySelector(`#${id}-motion`)
      const initial = animation.set.mock.calls.find(([target, vars]) => target === letter && vars.opacity === 0)![1]
      expect(initial.scaleX).not.toBe(initial.scaleY)
      const phases = animation.to.mock.calls.filter(([target]) => target === letter)
      expect(phases).toHaveLength(3)
      starts.push(phases[0][2])
      expect(phases[0][1].x).not.toBe(phases[1][1].x)
      expect(phases[2][1]).toMatchObject({ x: 0, y: 0, scaleX: 1, scaleY: 1, rotation: 0 })
      expect(Number((phases[2][2] + phases[2][1].duration).toFixed(3))).toBeLessThanOrEqual(1.45)
      expect(phases[0][1].duration + phases[1][1].duration + phases[2][1].duration).toBeCloseTo(1.18)
      expect(initial.scaleX).toBeGreaterThanOrEqual(.8)
      expect(initial.scaleY).toBeLessThanOrEqual(1.1)
      expect(Math.abs(initial.rotation)).toBeLessThanOrEqual(3)
    }
    expect(starts).toEqual([0, .035, .08, .15, .2, .27])
    expect(new Set(starts.slice(1).map((start, i) => Number((start - starts[i]).toFixed(3)))).size).toBeGreaterThan(1)
  })

  it('rotates the three locked O arrows clockwise exactly once and cleans up on unmount', () => {
    const { container, unmount } = render(<Hero onLogoReveal={vi.fn()} />)
    const spinner = container.querySelector('#o-spinner')!
    expect(spinner.querySelectorAll('path')).toHaveLength(3)
    const rotation = animation.fromTo.mock.calls.find(([target]) => 'angle' in target)!
    expect(rotation[1]).toEqual({ angle: 0 })
    expect(rotation[2]).toMatchObject({ angle: 360, ease: 'power3.out', duration: 1.25 })
    expect(rotation[2].repeat).toBeUndefined()
    rotation[0].angle = 180
    rotation[2].onUpdate()
    expect(spinner.getAttribute('transform')).toBe('rotate(180 -372.5 155)')
    rotation[0].angle = 360
    rotation[2].onUpdate()
    expect(spinner.getAttribute('transform')).toBe('rotate(360 -372.5 155)')
    expect(animation.to.mock.calls.some(([target]) => target instanceof Element && target.tagName === 'path')).toBe(false)
    unmount()
    expect(animation.revert).toHaveBeenCalledOnce()
    expect(spinner.hasAttribute('transform')).toBe(false)
  })

  it('reveals the tagline during logo settling without animating the centered wrapper', () => {
    const reveal = vi.fn()
    const { container } = render(<Hero onLogoReveal={reveal} />)
    const tagline = screen.getByRole('heading', { name: 'WASTE IS NOT THE END.' })
    expect(animation.fromTo).toHaveBeenCalledWith(tagline, { opacity: 0, y: 12 }, expect.objectContaining({ opacity: 1, y: 0, duration: .5 }), 1.3)
    const subcopy = container.querySelector('[data-hero-subcopy]')
    expect(animation.fromTo).toHaveBeenCalledWith(subcopy, { opacity: 0, y: 12 }, expect.objectContaining({ opacity: 1, y: 0, duration: .4 }), 1.4)
    expect(animation.call).toHaveBeenCalledWith(reveal, [], 1.45)
    const wrapper = container.querySelector('.hero__logo')
    expect([...animation.set.mock.calls, ...animation.to.mock.calls, ...animation.fromTo.mock.calls].some(([target]) => target === wrapper)).toBe(false)
    expect(container.querySelectorAll('.hero img')).toHaveLength(0)
  })

  it('shows the static logo and copy immediately when reduced motion is requested', () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true })))
    const reveal = vi.fn()
    const { container } = render(<Hero onLogoReveal={reveal} />)
    expect(animation.timeline).not.toHaveBeenCalled()
    expect(animation.set).toHaveBeenCalledWith(expect.any(Array), { clearProps: 'all', opacity: 1 })
    expect(reveal).toHaveBeenCalledOnce()
    expect(container.querySelector('#o-spinner')).not.toHaveAttribute('transform')
  })
  it('preserves animated SVG nodes when parent state causes a render', () => {
    const reveal = vi.fn()
    const { container, rerender } = render(<Hero onLogoReveal={reveal} />)
    const spinner = container.querySelector('#o-spinner')!
    spinner.setAttribute('transform', 'rotate(180 -372.5 155)')
    rerender(<Hero onLogoReveal={reveal} />)
    expect(container.querySelector('#o-spinner')).toBe(spinner)
    expect(spinner.getAttribute('transform')).toBe('rotate(180 -372.5 155)')
    expect(animation.timeline).toHaveBeenCalledOnce()
  })
  it('keeps I to the right of O until its final recovery without a leftward overshoot', () => {
    const { container } = render(<Hero onLogoReveal={vi.fn()} />)
    const iPhases = animation.to.mock.calls.filter(([target]) => target === container.querySelector('#i-motion'))
    const oPhases = animation.to.mock.calls.filter(([target]) => target === container.querySelector('#o-motion'))
    expect(iPhases[0][1].x).toBeGreaterThan(oPhases[0][1].x)
    expect(iPhases[0][1].x - oPhases[0][1].x).toBeLessThanOrEqual(30)
    expect(iPhases[1][1].x).toBeGreaterThan(0)
    expect(iPhases[1][1].x).toBeLessThanOrEqual(15)
    expect(iPhases[2][1].x).toBe(0)
  })
})
