// Product 도메인 타입. Backend `product_model` / `product_item` / `product_order` ENUM 과 일치한다.

/** `product_model.status` */
export type ProductModelStatus = 'DRAFT' | 'ON_SALE' | 'SOLD_OUT' | 'DISCONTINUED'

/** `product_item.status` — 개별 제품 단위 */
export type ProductItemStatus = 'IN_STOCK' | 'RESERVED' | 'SOLD'

/** `product_order.status` */
export type ProductOrderStatus = 'CREATED' | 'PAID' | 'SHIPPED' | 'DELIVERED'

/** `product_model.category` — 정책 v3 부록에서 6종으로 확정 */
export type ProductCategory = 'BAG' | 'WALLET' | 'CLOTHING' | 'ACCESSORY' | 'HOME' | 'ETC'

export interface ProductModelSummary {
  id: number
  name: string
  brandId: number
  price: number
  category: ProductCategory
  status: ProductModelStatus
  thumbnailUrl: string | null
}

// TODO(5번/Product 담당): 주문·배송 타입을 추가한다.
