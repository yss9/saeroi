package com.ssafy.upcycle.product.dto;

import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductOrderItem;

/**
 * 주문에 담긴 개체 한 점. 브랜드가 창고에서 물건을 집을 때 보는 값이다.
 *
 * <p>{@code itemCode} 와 {@code serialNumber} 가 핵심이다 — 같은 모델이라도 개체마다 다른
 * 물건이라, 이름만 보고 아무거나 집으면 QR 이 다른 제품이 나간다.
 *
 * <p>여정 주소는 없다. 그건 산 사람이 보는 값이다.
 */
public record BrandProductOrderItemResponse(
        String itemCode, int serialNumber, String productName, long price) {

    public static BrandProductOrderItemResponse of(
            ProductOrderItem orderItem, ProductItem item, ProductModel model) {
        return new BrandProductOrderItemResponse(
                item.getItemCode(),
                item.getSerialNumber(),
                model.getName(),
                orderItem.getPrice());
    }
}
