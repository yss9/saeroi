package com.ssafy.upcycle.user.dto;

/**
 * 소비자 BRANDS 화면에 공개하는 브랜드 정보.
 *
 * <p><b>사업자 정보를 담지 않는다.</b> {@code company_profile} 에는 사업자등록번호·주소·
 * 좌표가 함께 들어 있고 {@link CompanyProfileResponse} 가 그것을 사업자 본인에게 내보낸다.
 * 공개용은 필드를 빼는 것이 아니라 <b>처음부터 다른 record</b> 여야 한다 — 같은 DTO 를 쓰면
 * 나중에 사업자 화면에 필드 하나를 더할 때 그것이 소비자에게도 같이 나간다.
 *
 * <p>{@code brandId} 는 {@code users.id} 다. 상품 API 의 {@code brandId} 와 같은 값이라
 * {@code /api/v1/public/product-models?brandId=} 로 그 브랜드의 상품을 그대로 받을 수 있다.
 *
 * <p>{@code brandName} 은 회사명이 있으면 회사명, 없으면 사용자 이름이다 — 상품 목록의
 * 브랜드명·브랜드명 검색과 같은 규칙이고, 세 곳이 다른 이름을 보이면 사용자가 본 것으로
 * 검색해도 걸리지 않는다.
 *
 * <p>{@code introduction} 과 {@code logoUrl} 은 <b>{@code null} 일 수 있다.</b> 선택 항목이라
 * 채우지 않은 브랜드가 많고, 서버가 기본 문구나 기본 이미지를 대신 채우지 않는다 — 브랜드가
 * 쓰지 않은 글이 데이터처럼 내려가면 나중에 그 문구를 고칠 때마다 배포해야 한다. 빈 값일 때
 * 무엇을 보여줄지는 화면이 정한다. 빈 문자열은 내려가지 않는다: 저장 시점에 이미
 * {@code null} 로 접히고, 응답을 만들 때 한 번 더 접는다.
 *
 * @param productCount 판매 중이면서 소비자에게 노출되는 상품 수. 목록에 실린 브랜드는 언제나 1 이상
 */
public record PublicBrandResponse(
        Long brandId,
        String brandName,
        String introduction,
        String logoUrl,
        long productCount) {
}
