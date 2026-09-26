import { describe, expect, it } from 'vitest'
import { formatDate } from './formatDate.ts'

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

describe('formatDate', () => {
  it('formats a valid date string with the given formatter', () => {
    expect(formatDate('2026-09-16T10:00:00', dateFormatter)).toBe(dateFormatter.format(new Date('2026-09-16T10:00:00')))
  })

  it('accepts a Date instance', () => {
    const date = new Date('2026-01-02T03:04:05')
    expect(formatDate(date, dateFormatter)).toBe(dateFormatter.format(date))
  })

  it.each([null, undefined, '', 'not-a-date', '2026-13-45'])(
    'returns "-" instead of throwing for %s',
    (value) => {
      expect(formatDate(value, dateFormatter)).toBe('-')
    },
  )
})
