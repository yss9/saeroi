import type { StatusTone } from '../../../components/common/index.ts'
import type { CancellationItem, CancellationKind, CancellationStatus } from '../types/consumerCancellation.types.ts'

export const KIND_LABEL: Record<CancellationKind, string> = {
  CANCEL: '취소',
  RETURN: '반품',
  EXCHANGE: '교환',
  REFUND: '환불',
}

export const STATUS_META: Record<CancellationStatus, { label: string; tone: StatusTone }> = {
  REQUESTED: { label: '접수', tone: 'warning' },
  PROCESSING: { label: '처리중', tone: 'info' },
  DONE: { label: '완료', tone: 'success' },
  REJECTED: { label: '거절', tone: 'danger' },
}

/** 접수일 라벨 위에 붙는 문구. kind 별로 "취소/반품/교환/환불접수일"처럼 달라진다. */
export const RECEIVED_LABEL: Record<CancellationKind, string> = {
  CANCEL: '취소접수일',
  RETURN: '반품접수일',
  EXCHANGE: '교환접수일',
  REFUND: '환불접수일',
}

/** kind 별 상세/조회 버튼 라벨. 두 버튼 모두 같은 상세 화면(/mypage/cancellations/:id)으로 간다. */
export const ACTION_LABELS: Record<CancellationKind, readonly [string, string]> = {
  CANCEL: ['취소상세', '취소사유확인'],
  RETURN: ['반품상세', '회수조회'],
  EXCHANGE: ['교환상세', '회수조회'],
  REFUND: ['환불상세', '환불계좌확인'],
}

/**
 * 화면 확인용 목데이터. 백엔드가 아직 없다 — 취소/반품/교환/환불 통합 조회 API는 미착수.
 *
 * 목록 화면과 상세 화면(/mypage/cancellations/:id)이 같은 배열을 id로 찾아 쓴다 —
 * 목록에서 쓰던 값을 상세에서 다시 하드코딩하면 둘이 어긋난다.
 */
export const MOCK_CANCELLATION_ITEMS: readonly CancellationItem[] = [
  {
    id: '1',
    kind: 'CANCEL',
    status: 'DONE',
    productName: '업사이클 데님 토트백',
    orderNumber: 'ORD-00000012',
    orderedAt: '2026-09-07T10:00:00',
    requestedAt: '2026-09-10T11:20:00',
    itemCount: 1,
    reason: '단순 변심',
    amount: 89000,
  },
  {
    id: '2',
    kind: 'RETURN',
    status: 'PROCESSING',
    productName: '재생 울 니트',
    orderNumber: 'ORD-00000015',
    orderedAt: '2026-09-11T09:00:00',
    requestedAt: '2026-09-13T09:05:00',
    itemCount: 1,
    reason: '사이즈가 맞지 않음',
    amount: 132000,
  },
  {
    id: '3',
    kind: 'EXCHANGE',
    status: 'REQUESTED',
    productName: '패치워크 파우치',
    orderNumber: 'ORD-00000017',
    orderedAt: '2026-09-14T18:30:00',
    requestedAt: '2026-09-15T16:40:00',
    itemCount: 2,
    reason: '색상 교환 요청',
    amount: 39000,
  },
  {
    id: '4',
    kind: 'REFUND',
    status: 'REJECTED',
    productName: '스컬그레이몬',
    orderNumber: 'ORD-00000009',
    orderedAt: '2026-09-05T13:00:00',
    requestedAt: '2026-09-08T14:00:00',
    itemCount: 1,
    reason: '단순 변심(교환·반품 기간 경과)',
    amount: 5000,
  },
]
