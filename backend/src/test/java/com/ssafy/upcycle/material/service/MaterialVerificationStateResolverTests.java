package com.ssafy.upcycle.material.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.material.entity.MaterialVerificationState;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 판정 사다리만 검증한다. 근거를 어떻게 얻는지는 각 포트 구현의 몫이라 여기서는 스텁으로
 * 대체한다.
 *
 * <p><b>이 테스트가 지키는 것은 순서다.</b> 단을 추가할 때 아래에 잘못 끼우면 더 높이
 * 도달한 소재가 그 낮은 단으로 보고된다. 그건 화면에서 "덜 검증된 소재" 로 보이고,
 * 사용자는 그 차이를 설명받을 방법이 없다.
 */
class MaterialVerificationStateResolverTests {

    @Test
    void signedTradeRaisesTheMaterialToTransactionVerified() {
        MaterialVerificationStateResolver resolver = resolverWith(true, false);

        assertThat(resolver.resolve(1L))
                .isEqualTo(MaterialVerificationState.TRANSACTION_VERIFIED);
    }

    @Test
    void onChainVerifySourceRaisesTheMaterialToSourceVerified() {
        MaterialVerificationStateResolver resolver = resolverWith(false, true);

        assertThat(resolver.resolve(1L)).isEqualTo(MaterialVerificationState.SOURCE_VERIFIED);
    }

    @Test
    void materialWithNoEvidenceStaysRegistered() {
        MaterialVerificationStateResolver resolver = resolverWith(false, false);

        assertThat(resolver.resolve(1L)).isEqualTo(MaterialVerificationState.REGISTERED);
    }

    /**
     * 두 근거가 함께 있으면 <b>더 높은 단</b>이 이긴다.
     *
     * <p>실제로 흔한 조합이다 — 공급자가 출처를 서명한 소재를 브랜드가 사들이면 둘 다
     * 참이 된다. 낮은 쪽을 반환하면 거래까지 마친 소재가 "출처만 확인됨" 으로 보인다.
     */
    @Test
    void higherStageWinsWhenBothEvidencesExist() {
        MaterialVerificationStateResolver resolver = resolverWith(true, true);

        assertThat(resolver.resolve(1L))
                .isEqualTo(MaterialVerificationState.TRANSACTION_VERIFIED);
    }

    /** 판정 근거를 묻는 소재가 곧 요청된 소재여야 한다. 다른 id 를 넘기면 남의 기록으로 보고한다. */
    @Test
    void resolverAsksEveryPortAboutTheRequestedMaterial() {
        Long[] askedTrade = new Long[1];
        Long[] askedChain = new Long[1];
        MaterialVerificationStateResolver resolver = new MaterialVerificationStateResolver(
                materialId -> {
                    askedTrade[0] = materialId;
                    return false;
                },
                blockchainVerificationStub(materialId -> {
                    askedChain[0] = materialId;
                    return false;
                }));

        resolver.resolve(42L);

        assertThat(askedTrade[0]).isEqualTo(42L);
        assertThat(askedChain[0]).isEqualTo(42L);
    }

    /**
     * 도달할 수 없는 단을 만들어내지 않는다.
     *
     * <p>{@code PROCESSING_VERIFIED} 는 엔티티가 없고 {@code PRODUCT_LINKED} 는 온체인
     * 호출 경로가 "예약" 이다. 어떤 근거 조합으로도 이 두 값이 나오면 안 된다 — 나온다면
     * 누군가 판정할 수 없는 것을 판정하기 시작한 것이다.
     */
    @Test
    void neverReportsStagesThatHaveNoEvidenceSource() {
        for (boolean trade : new boolean[] {true, false}) {
            for (boolean chain : new boolean[] {true, false}) {
                assertThat(resolverWith(trade, chain).resolve(1L))
                        .as("trade=%s chain=%s", trade, chain)
                        .isNotIn(
                                MaterialVerificationState.PROCESSING_VERIFIED,
                                MaterialVerificationState.PRODUCT_LINKED);
            }
        }
    }

    private MaterialVerificationStateResolver resolverWith(
            boolean hasSignedTrade, boolean hasVerifiedSource) {
        return new MaterialVerificationStateResolver(
                materialId -> hasSignedTrade, blockchainVerificationStub(materialId -> hasVerifiedSource));
    }

    /**
     * {@link BlockchainVerification} 은 근거 조회({@code findSourceVerification})가
     * 추가되면서 더 이상 단일 추상 메서드가 아니다 — 판정 사다리 테스트는
     * {@code hasVerifiedSource} 만 스텁하면 되므로 근거 조회는 항상 빈 값으로 둔다.
     */
    private BlockchainVerification blockchainVerificationStub(
            java.util.function.Function<Long, Boolean> hasVerifiedSource) {
        return new BlockchainVerification() {
            @Override
            public boolean hasVerifiedSource(Long materialId) {
                return hasVerifiedSource.apply(materialId);
            }

            @Override
            public Optional<SourceVerificationEvidence> findSourceVerification(Long materialId) {
                return Optional.empty();
            }
        };
    }
}
