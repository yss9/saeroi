package com.ssafy.upcycle.evidence.entity;

import java.util.Set;

/** {@code evidence_file.evidence_type} — 어떤 종류의 증빙인가. */
public enum EvidenceType {
    BEFORE_PHOTO,
    AFTER_PHOTO,
    WEIGHT_SLIP,
    WEIGHT_PHOTO,
    INVOICE,
    PROCESS_PHOTO,
    ETC;

    /**
     * 관계 없는 제3자(예: 아직 사지 않은 마켓플레이스 구매자)에게도 열어 주는 종류인가.
     *
     * <p>계근표({@code WEIGHT_SLIP})·거래명세서({@code INVOICE})는 거래 상대·중량·단가가
     * 찍힌 문서라 {@link EvidenceAccessPolicy} 의 관계 판정을 그대로 받는다. 사진류만
     * 공개하는 이유는 구매 결정에 사진이 필요하기 때문이다 — 사진 없이 사라고 할 수 없다.
     */
    private static final Set<EvidenceType> PUBLICLY_VIEWABLE =
            Set.of(BEFORE_PHOTO, AFTER_PHOTO, WEIGHT_PHOTO, PROCESS_PHOTO);

    public boolean isPubliclyViewablePhoto() {
        return PUBLICLY_VIEWABLE.contains(this);
    }
}
