package com.ssafy.upcycle.prediction.config;

import java.math.BigInteger;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.prediction")
public record PredictionProperties(
        Long chainId,
        String rpcUrl,
        String marketAddress,
        String tokenAddress,
        String operatorPrivateKey,
        BigInteger welcomePointAmount,
        boolean oracleEnabled) {

    public boolean isReadable() {
        return chainId != null && chainId > 0 && text(rpcUrl)
                && address(marketAddress) && address(tokenAddress);
    }

    public boolean canWrite() { return isReadable() && text(operatorPrivateKey); }
    public BigInteger effectiveWelcomePointAmount() {
        return welcomePointAmount == null ? new BigInteger("1000000000000000000000") : welcomePointAmount;
    }
    private static boolean text(String value) { return value != null && !value.isBlank(); }
    private static boolean address(String value) { return value != null && value.matches("^0x[0-9a-fA-F]{40}$"); }
}
