import { useNavigate } from 'react-router-dom'
import { Button } from '../../../components/common/index.ts'

export interface ProductPurchasePanelProps { productModelId: number; purchasable: boolean; blockedReason?: string }

/** Product detail starts navigation; an order is created only after address review. */
export function ProductPurchasePanel({ productModelId, purchasable, blockedReason }: ProductPurchasePanelProps) {
  const navigate = useNavigate()
  return <div className="public-product__purchase">
    {blockedReason && <p>{blockedReason}</p>}
    <Button fullWidth disabled={!purchasable} onClick={() => navigate(`/products/${productModelId}/checkout`)}>구매하기</Button>
  </div>
}
