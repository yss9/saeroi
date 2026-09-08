package com.ssafy.upcycle.product.dto;

import com.ssafy.upcycle.common.storage.StoredFile;

/**
 * 제품 이미지 업로드 결과.
 *
 * <p>{@code storageKey} 를 제품 등록 요청의 {@code thumbnailUrl} 에 그대로 넣으면 된다.
 * <b>이 값은 화면에 붙일 주소가 아니다</b> — 이미지를 보는 경로는 등록이 끝난 뒤
 * {@code GET /api/v1/product-models/{id}/thumbnail} 이다.
 *
 * <p>키를 조회 주소로 쓰지 않는 이유는, 클라이언트가 키를 URL 에 넣게 되면 {@code ..} 나
 * 남의 키를 찔러 볼 수 있기 때문이다. 제품 id 로 받아 서버가 행에서 키를 꺼내면 그런 요청
 * 자체가 만들어지지 않는다.
 *
 * <p>등록까지 가지 않으면 이 파일은 아무도 가리키지 않는 채로 남는다. 정리는 별도 배치의
 * 몫이고, 증빙 업로드도 같은 구조다.
 *
 * @param hash 기록된 바이트의 SHA-256. 같은 사진을 다시 올렸는지 프론트가 판단할 수 있다
 */
public record ProductImageUploadResponse(
        String storageKey, String originalFilename, String contentType, long size, String hash) {

    public static ProductImageUploadResponse from(StoredFile stored) {
        return new ProductImageUploadResponse(
                stored.storageKey(),
                stored.originalFilename(),
                stored.contentType(),
                stored.size(),
                stored.hash());
    }
}
