package com.ssafy.upcycle.prediction.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PredictionProperties.class)
public class PredictionConfig {
}
