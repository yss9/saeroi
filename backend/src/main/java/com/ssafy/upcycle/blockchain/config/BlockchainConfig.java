package com.ssafy.upcycle.blockchain.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 이 프로젝트는 {@code @ConfigurationPropertiesScan} 을 쓰지 않는다 — 어떤 설정이 살아 있는지
 * 한 곳에서 보이게 하려고 도메인마다 명시적으로 등록한다 ({@code WalletAuthConfig} 와 같은 방식).
 */
@Configuration
@EnableConfigurationProperties(BlockchainProperties.class)
@RequiredArgsConstructor
@Slf4j
public class BlockchainConfig {

    private final BlockchainProperties properties;

    /**
     * 온체인 연동이 켜졌는지 시작 로그에 남긴다.
     *
     * <p>{@code PaymentGatewayConfig} 가 카카오페이 키 유무를 남기는 것과 같은 이유다 —
     * 설정이 빠진 채로 뜨면 <b>사용자가 지갑 서명까지 마친 뒤에야</b> 501 을 본다. 그 시점에는
     * 트랜잭션이 이미 체인에 올라가 있어 되돌릴 수 없다. 기동할 때 알 수 있어야 한다.
     */
    @PostConstruct
    void logConfiguration() {
        if (properties.isConfigured()) {
            log.info("Blockchain link enabled: chainId={} materialToken={} marketplace={}",
                    properties.chainId(),
                    properties.materialTokenAddress(),
                    properties.marketplaceAddress());
            // **주소만 있고 rpc-url 이 비면 읽기가 조용히 실패한다.** isConfigured() 는
            // 주소 3개만 보므로 위 로그가 "enabled" 라고 말하는데도 체인을 읽을 수 없다.
            // 실제로 수령 서명이 남았는지 판정하는 값이 항상 false 로 나왔고, 원인을
            // 찾는 데 오래 걸렸다 — 실패하는 자리에 로그가 없었기 때문이다.
            if (properties.rpcUrl() == null || properties.rpcUrl().isBlank()) {
                log.warn("Blockchain rpc-url is EMPTY - the server cannot READ the chain. "
                        + "Receipt verification and on-chain status checks will silently "
                        + "return 'unknown'. Set BESU_RPC_URL or app.blockchain.rpc-url.");
            }
        } else {
            log.warn("Blockchain link is NOT configured - chain endpoints will return 501. "
                            + "chainId={} materialToken={} marketplace={} "
                            + "(set app.blockchain.* in application-local.yml)",
                    properties.chainId(),
                    properties.materialTokenAddress(),
                    properties.marketplaceAddress());
        }
    }
}
