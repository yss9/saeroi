package com.ssafy.upcycle.product.demo;

import com.ssafy.upcycle.product.entity.ProductImageType;

/**
 * 시연 상품에 올릴 이미지 한 장. <b>아직 저장되지 않은 바이트다.</b>
 *
 * <p>출처가 둘이다 — 운영자가 넣어 둔 진짜 이미지({@link DemoProductImageDirectory})이거나,
 * 그것이 없을 때 만들어 쓰는 임시 이미지({@link DemoPlaceholderImageFactory}). 어느 쪽이든
 * 같은 모양으로 {@link DemoProductImageBinder} 에 전달되어 같은 경로로 저장된다.
 *
 * <p>{@code byte[]} 로 들고 있는 것은 <b>같은 바이트를 두 번 써야 하기 때문</b>이다: 저장소에
 * 올리고, 그 전에 해시를 계산해 지금 걸려 있는 이미지와 같은 것인지 본다. 시연 이미지는
 * 장당 수십 KB 라 스트림으로 흘릴 이유가 없다.
 *
 * @param type 갤러리에서의 자리. 대표 이미지는 {@link ProductImageType#MAIN}
 * @param filename 저장 시 확장자 검증에 쓰인다. 저장 이름 자체는 저장소가 UUID 로 정한다
 * @param contentType 확장자와 일치해야 한다 — 저장소가 대조한다
 */
public record DemoProductImage(
        ProductImageType type,
        String filename,
        String contentType,
        byte[] content) {

    public long size() {
        return content.length;
    }
}
