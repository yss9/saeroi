package com.ssafy.upcycle.user.entity;

/**
 * users.role ENUM 과 1:1 로 대응한다.
 *
 * <p>이 이름이 그대로 JWT 의 {@code roles} 클레임에 들어가고, 읽는 쪽에서
 * {@code JwtAuthorityConverter} 가 {@code ROLE_} 접두어를 붙인다.
 * 따라서 여기에 {@code ROLE_} 를 넣으면 안 된다 — {@code JwtTokenService} 가 거부한다.
 */
public enum UserRole {
    SUPPLIER,
    BRAND,
    PROCESSOR,
    CONSUMER,
    ADMIN;

    /**
     * 지갑을 가질 수 있는 역할인가.
     *
     * <p><b>DB 에는 이걸 막는 제약이 없다.</b> 시드나 향후 등록 API 가 잘못 넣으면
     * 지갑 로그인이 그 계정에 토큰을 발급해 버리므로, 역할 경계를 코드에서 강제한다.
     * 역할 목록을 여기 한 곳에만 두어 지갑 등록 API 가 생겨도 같은 기준을 쓰게 한다.
     */
    public boolean canOwnWallet() {
        return this == SUPPLIER || this == BRAND || this == PROCESSOR;
    }

    /**
     * 이메일 로그인 뒤 지갑을 연결할 수 있는 역할인가.
     *
     * <p>사업자 지갑 로그인 대상은 기존 세 역할로 유지한다. 소비자는 일반 쇼핑에는 지갑이
     * 필요 없지만 예측시장에 참여할 때만 MetaMask를 연결한다. ADMIN은 결과 통제 주체라
     * 연결 대상에서 제외한다.
     */
    public boolean canLinkWallet() {
        return canOwnWallet() || this == CONSUMER;
    }

    /**
     * 소재를 등록할 수 있는 역할인가.
     *
     * <p>{@code SUPPLIER} 와 {@code BRAND} 만이다. <b>{@code PROCESSOR} 가 빠진 것이 핵심이다</b> —
     * 가공업체가 만드는 소재는 가공 확정의 산출물({@code PROCESSED})이지 직접 등록하는 대상이
     * 아니다. 등록을 열어 주면 가공 이력 없는 소재를 가공업체 이름으로 올릴 수 있게 되고,
     * 그러면 "모든 폐소재는 등록 가공업체를 거쳐 상태·수량이 검증된다" 는 전제가 무너진다.
     * {@code CONSUMER}·{@code ADMIN} 은 공급 주체가 아니다.
     *
     * <p>{@link #canOwnWallet} 과 목록이 <b>다르다</b>. 지갑을 가질 수 있다는 것과 소재를
     * 올릴 수 있다는 것은 별개 권한이며, 한 판정으로 합치면 둘 중 하나가 조용히 넓어진다.
     */
    public boolean canRegisterMaterial() {
        return this == SUPPLIER || this == BRAND;
    }

    /**
     * 제품을 등록할 수 있는 역할인가.
     *
     * <p>{@code BRAND} 뿐이다. 업사이클 제품을 파는 주체가 브랜드이고, 제품 모델은 곧
     * 마켓에 노출되는 판매 단위라 공급·가공 주체가 직접 올릴 대상이 아니다.
     *
     * <p>{@link #canRegisterMaterial} 과 목록이 <b>다르다</b>. 소재를 올릴 수 있다는 것과
     * 제품을 팔 수 있다는 것은 별개 권한이며, 합치면 {@code SUPPLIER} 가 제품을 올릴 수 있게
     * 조용히 넓어진다.
     */
    public boolean canRegisterProduct() {
        return this == BRAND;
    }

    /**
     * 배송지 주소록({@code consumer_address})을 갖는 역할인가.
     *
     * <p>{@code CONSUMER} 뿐이다. 사업자 3종은 {@code company_profile} 이 주소를 갖는다 —
     * 한 계정이 두 주소 체계를 갖게 두면 "어디로 보내야 하는가" 에 답이 둘이 된다.
     * {@code ADMIN} 은 물건을 받는 주체가 아니다.
     *
     * <p>{@link #canOwnWallet} 의 정확한 여집합처럼 보이지만 <b>그렇게 쓰면 안 된다</b>.
     * {@code ADMIN} 이 양쪽 모두에서 빠지기 때문이고, 두 판정이 우연히 맞물린 것을 규칙으로
     * 굳히면 역할이 하나 늘어날 때 조용히 어긋난다.
     */
    public boolean hasDeliveryAddressBook() {
        return this == CONSUMER;
    }

    /**
     * 1:1 문의를 등록할 수 있는 역할인가.
     *
     * <p>{@code CONSUMER} 뿐이다. 사업자는 각자의 계약·거래 채널이 따로 있고, 이 문의함은
     * 소비자가 주문(배송·교환/반품·상품)에 대해 묻는 자리다.
     */
    public boolean canFileInquiry() {
        return this == CONSUMER;
    }
}
