import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, expect, test } from 'vitest'
import { processingHistoryText } from '../utils/processingHistory.ts'
import { MaterialProcessingBadge } from './MaterialProcessingBadge.tsx'

afterEach(cleanup)

// 가공을 거쳐도 소재 이름은 바뀌지 않는다. 같은 이름의 소재를 가르는 것은 이 배지다.
test('가공한 적이 없는 소재는 가공 전으로 표시한다', () => {
  render(<MaterialProcessingBadge completedProcessTypes={[]} />)

  expect(screen.getByText('가공 전')).toHaveClass('material-processing-badge', 'is-raw')
})

test('거친 가공 종류를 순서대로 모두 보여준다', () => {
  render(<MaterialProcessingBadge completedProcessTypes={['CLEANING', 'CUTTING']} />)

  expect(screen.getByText('세척·재단 가공')).toHaveClass('material-processing-badge', 'is-processed')
})

test('생산에서 나온 자투리는 그 사실을 함께 말한다', () => {
  render(<MaterialProcessingBadge completedProcessTypes={['CLEANING']} generationType="PRODUCTION_SCRAP" />)

  expect(screen.getByText('생산 자투리 · 세척 가공')).toBeInTheDocument()
})

test('선택지 문구에도 같은 말을 쓴다', () => {
  expect(processingHistoryText([])).toBe('가공 전')
  expect(processingHistoryText(['SORTING'])).toBe('선별 가공')
  expect(processingHistoryText([], 'PRODUCTION_SCRAP')).toBe('생산 자투리 · 가공 전')
})

test('이력을 모르면 가공 전이라고 단정하지 않는다', () => {
  const { container } = render(<MaterialProcessingBadge completedProcessTypes={undefined} />)

  expect(container).toBeEmptyDOMElement()
})
