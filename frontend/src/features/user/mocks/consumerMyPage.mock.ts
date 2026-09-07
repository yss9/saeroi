import type {
  ConsumerMyPageData,
  ConsumerProfileSummary,
} from '../types/consumerMyPage.types.ts'

/**
 * UI 검증용 임시 데이터.
 *
 * 실제 API가 연결되면 이 팩토리 대신 Feature API + TanStack Query 훅이
 * ConsumerMyPageData를 만들도록 교체한다. 화면 컴포넌트에는 목 데이터가 없다.
 */
export function createConsumerMyPageMock(
  profile: ConsumerProfileSummary,
): ConsumerMyPageData {
  return {
    profile,
    orderStages: [
      { stage: 'PAYMENT_COMPLETED', count: 1 },
      { stage: 'PREPARING', count: 1 },
      { stage: 'SHIPPING', count: 1 },
      { stage: 'DELIVERED', count: 4 },
    ],
    recentOrders: [
      {
        id: 12048,
        orderNumber: 'SAE-20260905-12048',
        orderedAt: '2026-09-05T10:30:00+09:00',
        productName: '데님 서클 백',
        brandName: 'REWEAVE',
        thumbnailUrl: '/images/landing/product-denim-circle.webp',
        thumbnailAlt: '업사이클 데님 서클 백',
        itemCount: 1,
        totalAmount: 129000,
        status: 'SHIPPING',
      },
      {
        id: 11987,
        orderNumber: 'SAE-20260829-11987',
        orderedAt: '2026-08-29T14:10:00+09:00',
        productName: '마켓 토트',
        brandName: 'RE:CODE',
        thumbnailUrl: '/images/landing/product-market-tote.webp',
        thumbnailAlt: '업사이클 마켓 토트백',
        itemCount: 1,
        totalAmount: 79000,
        status: 'DELIVERED',
      },
    ],
    defaultAddress: {
      id: 1,
      recipientName: profile.name,
      phone: '010-1234-5678',
      postalCode: '06236',
      addressLine1: '서울특별시 강남구 테헤란로 212',
      addressLine2: '새로이 빌딩 5층',
      isDefault: true,
    },
  }
}
