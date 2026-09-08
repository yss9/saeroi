package com.ssafy.upcycle.blockchain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 배포된 컨트랙트의 좌표. 정본은 {@code blockchain/deployments/ssafy.json} 이다.
 *
 * <p><b>화면이 보내는 주소를 믿지 않기 위해 서버가 들고 있다.</b> 체인 ID 와 컨트랙트 주소를
 * 요청 바디로 받으면, 사용자가 자기가 만든 컨트랙트 주소를 넣어 "온체인에 등록됨" 으로
 * 기록시킬 수 있다. 체인에서 다시 확인하지 않는 이상 그것을 구분할 방법이 없다.
 *
 * <p>재배포하면 이 값을 {@code deployments/ssafy.json} 내용으로 함께 갱신한다.
 * 프론트의 {@code VITE_*_CONTRACT_ADDRESS} 와 <b>같은 배포분</b>이어야 한다 — 어긋나면
 * 화면은 새 컨트랙트에 서명하는데 서버는 옛 주소로 기록한다.
 */
@ConfigurationProperties(prefix = "app.blockchain")
public record BlockchainProperties(
        Long chainId, String materialTokenAddress, String marketplaceAddress) {

    /** 주소가 하나라도 비어 있으면 온체인 기록을 받지 않는다. */
    public boolean isConfigured() {
        return chainId != null && chainId > 0
                && hasAddress(materialTokenAddress)
                && hasAddress(marketplaceAddress);
    }

    private static boolean hasAddress(String address) {
        return address != null && address.length() == 42 && address.startsWith("0x");
    }
}
