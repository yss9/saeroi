package com.ssafy.upcycle.product.demo;

import com.ssafy.upcycle.product.dto.ProductModelImagesReplaceRequest;
import java.util.List;

/**
 * 저장소에 올라간 데모 이미지의 키 묶음. 아직 상품에 걸리지는 않은 상태다.
 *
 * <p>올리는 일과 거는 일을 나눈 이유는 <b>상품이 생기기 전에 대표 이미지 키가 필요하기</b>
 * 때문이다 — {@code thumbnail_url} 이 {@code NOT NULL} 이다. 먼저 올려 키를 받고, 상품 행이
 * 생긴 뒤 갤러리를 건다. 한 번에 하려 하면 같은 파일을 두 번 올리게 된다.
 *
 * @param thumbnailKey 대표 이미지 저장 키. {@code product_model.thumbnail_url} 에 들어간다
 * @param gallery 상세 갤러리에 걸 이미지들. <b>임시 이미지 상태에서는 비어 있다</b>
 */
public record DemoProductImageUpload(
        String thumbnailKey,
        List<ProductModelImagesReplaceRequest.Image> gallery) {
}
