package com.ssafy.upcycle.user.dto;

/**
 * 브랜드 로고 업로드 결과.
 *
 * <p><b>저장 키를 내보내지 않는다.</b> 키는 저장소 안의 경로라 화면이 쓸 수 없고, 내보내면
 * 프론트가 그것으로 주소를 조립하려 든다 — 그 순간 저장 방식이 화면에 새고, 로컬에서 되던
 * 것이 S3 배포에서 깨진다. 상품 이미지가 같은 판단을 한다.
 *
 * @param logoUrl 바이트를 흘려 주는 주소. 공개 경로라 브랜드 본인 화면도 그대로 쓸 수 있다
 */
public record BrandLogoUploadResponse(String logoUrl) {
}
