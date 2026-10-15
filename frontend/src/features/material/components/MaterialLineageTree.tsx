import { Link } from 'react-router-dom'
import { EmptyState, ErrorMessage, Loading } from '../../../components/common/index.ts'
import { useMaterialLineage } from '../hooks/useMaterialLineage.ts'
import { useClickNavigation } from '../../../utils/useClickNavigation.ts'
import {
  LINEAGE_RELATION_LABEL,
  type MaterialLineage,
  type MaterialLineageNode,
} from '../types/material.types.ts'

export interface MaterialLineageTreeProps {
  materialId: number | null
}

/**
 * 소재 혈통. 티켓(S15P21E104-204)이 요구한 모양이다.
 *
 * <pre>
 *   MAT-001
 *      ↓ 가공
 *   MAT-004
 * </pre>
 *
 * <b>세대별로 묶어 위에서 아래로 그린다.</b> 서버는 노드 + 간선을 주는데, 노드에
 * `depth`(조상 음수 · 대상 0 · 자손 양수)가 있어 그것만으로 세대를 만들 수 있다.
 * 간선을 따라 재귀로 그리면 자투리 재투입처럼 같은 소재가 두 경로에 있을 때 같은
 * 카드가 두 번 나온다 — 사용자는 소재가 두 개라고 읽는다.
 *
 * 화살표 옆 라벨은 <b>그 세대로 들어온 간선</b>의 종류다. 한 세대에 종류가 섞이면
 * (가공 자식과 자투리 자식이 함께) 둘 다 보여준다 — 하나만 골라 쓰면 나머지가 거짓이 된다.
 */
export function MaterialLineageTree({ materialId }: MaterialLineageTreeProps) {
  const { data, isPending, isError, error, refetch } = useMaterialLineage(materialId)

  if (isPending) return <Loading variant="skeleton" rows={3} label="소재 이력을 불러오는 중" />
  if (isError) return <ErrorMessage error={error} onRetry={() => void refetch()} />

  const generations = toGenerations(data)
  if (generations.length <= 1) {
    return (
      <EmptyState
        title="연결된 이력이 없습니다"
        // 최초 등록 소재는 부모가 없고, 아직 가공되지 않았으면 자식도 없다.
        // 그건 정상 상태이므로 오류처럼 보이지 않게 한다.
        description="가공이나 생산을 거치면 부모·자식 소재가 여기에 표시됩니다."
      />
    )
  }

  return (
    <div className="material-lineage">
      {data.truncated && data.message && (
        // 서버가 상한(깊이 5 · 노드 200)에 걸려 일부만 보냈다. 이걸 숨기면
        // 사용자는 잘린 이력을 전체로 믿는다.
        <p className="material-lineage__truncated" role="status">
          {data.message}
        </p>
      )}

      <ol className="material-lineage__generations">
        {generations.map((generation, index) => (
          <li key={generation.depth}>
            {index > 0 && (
              <p className="material-lineage__arrow" aria-label={arrowLabel(generation)}>
                ↓ {generation.relationLabels.join(' · ')}
              </p>
            )}
            <ul className="material-lineage__nodes">
              {generation.nodes.map((node) => (
                <li key={node.materialId}>
                  <MaterialLineageCard node={node} isTarget={node.depth === 0} />
                </li>
              ))}
            </ul>
          </li>
        ))}
      </ol>
    </div>
  )
}

function MaterialLineageCard({
  node,
  isTarget,
}: {
  node: MaterialLineageNode
  isTarget: boolean
}) {
  const followClick = useClickNavigation()
  // 조회 중인 소재는 링크로 만들지 않는다. 자기 페이지로 가는 링크는 눌러도
  // 아무 일이 없어 사용자가 고장으로 읽는다.
  return (
    <article
      className={`material-lineage__card${isTarget ? ' material-lineage__card--current' : ' material-lineage__card--clickable'}`}
      onClick={isTarget ? undefined : (event) => followClick(event, `/materials/${node.materialId}`)}
    >
      <p className="material-lineage__code">
        {isTarget ? node.materialCode : <Link to={`/materials/${node.materialId}`}>{node.materialCode}</Link>}
      </p>
      <p className="material-lineage__name">{node.name}</p>
      {isTarget && <span className="material-lineage__badge">지금 보는 소재</span>}
    </article>
  )
}

interface Generation {
  depth: number
  nodes: MaterialLineageNode[]
  /** 이 세대로 들어온 간선의 종류. 섞이면 여러 개다 */
  relationLabels: string[]
}

/**
 * 노드를 `depth` 로 묶어 위(조상)에서 아래(자손) 순서로 만든다.
 *
 * 세대 사이 라벨은 간선에서 가져온다. 어떤 간선이 "이 세대로 들어왔는가" 는
 * 간선의 양 끝 노드 깊이로 판정한다 — 자손 방향이면 자식이 이 세대, 조상 방향이면
 * 부모가 이 세대다.
 */
function toGenerations(lineage: MaterialLineage): Generation[] {
  const depthById = new Map(lineage.nodes.map((node) => [node.materialId, node.depth]))
  const byDepth = new Map<number, MaterialLineageNode[]>()
  for (const node of lineage.nodes) {
    const bucket = byDepth.get(node.depth) ?? []
    bucket.push(node)
    byDepth.set(node.depth, bucket)
  }

  return [...byDepth.keys()]
    .sort((a, b) => a - b)
    .map((depth) => {
      const labels = new Set<string>()
      for (const edge of lineage.edges) {
        const childDepth = depthById.get(edge.childMaterialId)
        const parentDepth = depthById.get(edge.parentMaterialId)
        // 부모가 위 세대이고 자식이 이 세대인 간선만 이 세대로 "들어온" 것이다.
        if (childDepth === depth && parentDepth !== undefined && parentDepth < depth) {
          labels.add(LINEAGE_RELATION_LABEL[edge.relationType])
        }
      }
      return {
        depth,
        nodes: byDepth.get(depth) ?? [],
        relationLabels: labels.size > 0 ? [...labels] : ['연결'],
      }
    })
}

function arrowLabel(generation: Generation): string {
  return `${generation.relationLabels.join(', ')}으로 생성됨`
}
