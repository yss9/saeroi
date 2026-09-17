package com.ssafy.upcycle.prediction.service;

import com.ssafy.upcycle.prediction.config.PredictionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PredictionOracleWorker {
    private final PredictionProperties properties;
    private final PredictionOracleService oracle;

    @Scheduled(fixedDelayString = "${app.prediction.oracle-delay-ms:60000}")
    public void resolveDueMarkets() {
        if (properties.oracleEnabled()) oracle.resolveDueMarkets();
    }
}
