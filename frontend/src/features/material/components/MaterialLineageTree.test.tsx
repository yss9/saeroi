// 소재 혈통 트리.
//
// 여기서 막는 거짓말 셋:
//   1) 잘린 이력을 전체처럼 보여준다 (truncated 무시)
//   2) 같은 소재를 두 번 그린다 (자투리 재투입 · 1:N 해체)
//   3) 이력이 없는 것을 오류로 보여준다
//
// 모킹 경계는 API 모듈 하나다. vitest 에 globals 가 없어 cleanup 을 직접 부른다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import type { MaterialLineage, MaterialLineageNode } from '../types/material.types.ts'
import { MaterialLineageTree } from './MaterialLineageTree.tsx'

const api = vi.hoisted(() => ({ getMaterialLineage: vi.fn() }))
vi.mock('../api/getMaterialLineage.ts', () => api)

function node(id: number, depth: number, code?: string): MaterialLineageNode {
  return {
    materialId: id,
    materialCode: code ?? `MAT-00000${id}`,
    materialType: 'DENIM',
    rootOrigin: 'WASTE',
    generationType: depth === 0 ? 'INITIAL' : 'PROCESSED',
    name: `소재 ${id}`,
    status: 'ACTIVE',
    depth,
  }
}

function lineage(overrides: Partial<MaterialLineage> = {}): MaterialLineage {
  return {
    materialId: 2,
    materialCode: 'MAT-000002',
    nodes: [node(1, -1), node(2, 0)],
    edges: [
      { parentMaterialId: 1, childMaterialId: 2, relationType: 'PROCESSING', referenceId: 7001 },
    ],
    truncated: false,
    message: null,
    ...overrides,
  }
}

function renderTree(materialId: number | null = 2) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <MaterialLineageTree materialId={materialId} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  api.getMaterialLineage.mockReset()
})

afterEach(cleanup)

describe('잘린 이력 표기', () => {
  it('truncated 면 서버 문구를 그대로 보여준다', async () => {
    // 이걸 숨기면 사용자는 잘린 이력을 전체로 믿는다.
    api.getMaterialLineage.mockResolvedValue(
      lineage({ truncated: true, message: '이전 이력은 일부 생략되었습니다' }),
    )
    renderTree()

    expect(await screen.findByText('이전 이력은 일부 생략되었습니다')).toBeInTheDocument()
  })

  it('truncated 가 아니면 그 문구를 보여주지 않는다', async () => {
    api.getMaterialLineage.mockResolvedValue(lineage())
    renderTree()

    await screen.findByText('MAT-000001')
    expect(screen.queryByText(/일부 생략/)).not.toBeInTheDocument()
  })
})

describe('세대 구성', () => {
  it('조상을 위에, 조회 대상을 아래에 그린다', async () => {
    api.getMaterialLineage.mockResolvedValue(lineage())
    renderTree()

    await screen.findByText('MAT-000001')
    const codes = screen.getAllByText(/^MAT-\d{6}$/).map((el) => el.textContent)
    expect(codes).toEqual(['MAT-000001', 'MAT-000002'])
  })

  it('세대 사이에 간선 종류를 표시한다', async () => {
    // 티켓이 요구한 "MAT-001 ↓ Processing MAT-004" 의 화살표 라벨.
    api.getMaterialLineage.mockResolvedValue(lineage())
    renderTree()

    expect(await screen.findByText(/↓ 가공/)).toBeInTheDocument()
  })

  it('자투리 간선은 다른 라벨을 쓴다', async () => {
    api.getMaterialLineage.mockResolvedValue(
      lineage({
        edges: [
          {
            parentMaterialId: 1,
            childMaterialId: 2,
            relationType: 'PRODUCTION_SCRAP',
            referenceId: 8001,
          },
        ],
      }),
    )
    renderTree()

    expect(await screen.findByText(/↓ 생산 자투리/)).toBeInTheDocument()
  })

  it('한 세대에 종류가 섞이면 둘 다 보여준다', async () => {
    // 하나만 골라 쓰면 나머지가 거짓이 된다.
    api.getMaterialLineage.mockResolvedValue(
      lineage({
        materialId: 1,
        materialCode: 'MAT-000001',
        nodes: [node(1, 0), node(2, 1), node(3, 1)],
        edges: [
          { parentMaterialId: 1, childMaterialId: 2, relationType: 'PROCESSING', referenceId: 7001 },
          {
            parentMaterialId: 1,
            childMaterialId: 3,
            relationType: 'PRODUCTION_SCRAP',
            referenceId: 8001,
          },
        ],
      }),
    )
    renderTree(1)

    const arrow = await screen.findByText(/↓/)
    expect(arrow.textContent).toContain('가공')
    expect(arrow.textContent).toContain('생산 자투리')
  })

  it('1:N 해체에서 자식을 한 세대에 나란히 그린다', async () => {
    api.getMaterialLineage.mockResolvedValue(
      lineage({
        materialId: 1,
        materialCode: 'MAT-000001',
        nodes: [node(1, 0), node(2, 1), node(3, 1), node(4, 1)],
        edges: [2, 3, 4].map((child) => ({
          parentMaterialId: 1,
          childMaterialId: child,
          relationType: 'PROCESSING' as const,
          referenceId: 7000 + child,
        })),
      }),
    )
    renderTree(1)

    await screen.findByText('MAT-000002')
    expect(screen.getAllByText(/^MAT-\d{6}$/)).toHaveLength(4)
  })

  it('같은 소재를 두 번 그리지 않는다', async () => {
    // 자투리 재투입처럼 같은 쌍이 두 작업으로 이어져도 카드는 하나여야 한다.
    // 간선을 따라 재귀로 그리면 여기서 중복이 생긴다.
    api.getMaterialLineage.mockResolvedValue(
      lineage({
        edges: [
          { parentMaterialId: 1, childMaterialId: 2, relationType: 'PROCESSING', referenceId: 7001 },
          { parentMaterialId: 1, childMaterialId: 2, relationType: 'PROCESSING', referenceId: 7002 },
        ],
      }),
    )
    renderTree()

    await screen.findByText('MAT-000001')
    expect(screen.getAllByText('MAT-000001')).toHaveLength(1)
    expect(screen.getAllByText('MAT-000002')).toHaveLength(1)
  })
})

describe('링크', () => {
  it('다른 소재는 상세로 이동하는 링크다', async () => {
    api.getMaterialLineage.mockResolvedValue(lineage())
    renderTree()

    const link = await screen.findByRole('link', { name: 'MAT-000001' })
    expect(link).toHaveAttribute('href', '/materials/1')
  })

  it('조회 중인 소재는 링크로 만들지 않는다', async () => {
    // 자기 페이지로 가는 링크는 눌러도 아무 일이 없어 사용자가 고장으로 읽는다.
    api.getMaterialLineage.mockResolvedValue(lineage())
    renderTree()

    await screen.findByText('MAT-000002')
    expect(screen.queryByRole('link', { name: 'MAT-000002' })).not.toBeInTheDocument()
    expect(screen.getByText('지금 보는 소재')).toBeInTheDocument()
  })
})

describe('세 가지 상태', () => {
  it('조회 중에는 로딩을 보여준다', () => {
    api.getMaterialLineage.mockReturnValue(new Promise(() => {}))
    renderTree()

    expect(screen.getByRole('status', { name: '소재 이력을 불러오는 중' })).toBeInTheDocument()
  })

  it('이력이 없으면 EmptyState 다 — 오류가 아니다', async () => {
    // 최초 등록 소재는 부모가 없고 아직 가공되지 않았으면 자식도 없다. 정상 상태다.
    api.getMaterialLineage.mockResolvedValue(
      lineage({ nodes: [node(2, 0)], edges: [] }),
    )
    renderTree()

    expect(await screen.findByText('연결된 이력이 없습니다')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '다시 시도' })).not.toBeInTheDocument()
  })

  it('실패하면 ErrorMessage 다', async () => {
    api.getMaterialLineage.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, { message: '이력을 불러오지 못했습니다.' }),
    )
    renderTree()

    expect(await screen.findByText('이력을 불러오지 못했습니다.')).toBeInTheDocument()
    expect(screen.queryByText('연결된 이력이 없습니다')).not.toBeInTheDocument()
  })

  it('materialId 가 null 이면 요청하지 않는다', () => {
    renderTree(null)

    expect(api.getMaterialLineage).not.toHaveBeenCalled()
  })
})

describe('상한 데이터', () => {
  it('깊이 5 · 여러 세대를 모두 그린다', async () => {
    const nodes = [-5, -4, -3, -2, -1, 0].map((depth, index) => node(index + 1, depth))
    api.getMaterialLineage.mockResolvedValue(
      lineage({
        nodes,
        edges: nodes.slice(1).map((child, index) => ({
          parentMaterialId: nodes[index].materialId,
          childMaterialId: child.materialId,
          relationType: 'PROCESSING' as const,
          referenceId: 7000 + index,
        })),
        truncated: true,
        message: '이전 이력은 일부 생략되었습니다',
      }),
    )
    renderTree(6)

    await waitFor(() => expect(screen.getAllByText(/^MAT-\d{6}$/)).toHaveLength(6))
    expect(screen.getByText('이전 이력은 일부 생략되었습니다')).toBeInTheDocument()
  })
})
