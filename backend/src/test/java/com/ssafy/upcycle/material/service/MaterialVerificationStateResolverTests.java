package com.ssafy.upcycle.material.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.material.entity.MaterialVerificationState;
import org.junit.jupiter.api.Test;

/**
 * 판정 사다리만 검증한다. 근거를 어떻게 얻는지는 {@link TradeVerification} 구현의 몫이라
 * 여기서는 스텁으로 대체한다.
 */
class MaterialVerificationStateResolverTests {

    @Test
    void signedTradeRaisesTheMaterialToTransactionVerified() {
        MaterialVerificationStateResolver resolver = resolverWith(true);

        assertThat(resolver.resolve(1L))
                .isEqualTo(MaterialVerificationState.TRANSACTION_VERIFIED);
    }

    @Test
    void materialWithoutSignedTradeStaysRegistered() {
        MaterialVerificationStateResolver resolver = resolverWith(false);

        assertThat(resolver.resolve(1L)).isEqualTo(MaterialVerificationState.REGISTERED);
    }

    /**
     * 판정 근거를 묻는 소재가 곧 요청된 소재여야 한다. 다른 id 를 넘기면 남의 거래로
     * 검증됐다고 보고하게 된다.
     */
    @Test
    void resolverAsksAboutTheRequestedMaterial() {
        Long[] asked = new Long[1];
        MaterialVerificationStateResolver resolver =
                new MaterialVerificationStateResolver(materialId -> {
                    asked[0] = materialId;
                    return false;
                });

        resolver.resolve(42L);

        assertThat(asked[0]).isEqualTo(42L);
    }

    private MaterialVerificationStateResolver resolverWith(boolean hasSignedTrade) {
        return new MaterialVerificationStateResolver(materialId -> hasSignedTrade);
    }
}
