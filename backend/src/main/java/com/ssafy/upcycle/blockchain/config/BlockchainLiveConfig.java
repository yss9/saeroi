package com.ssafy.upcycle.blockchain.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 블록 폴러가 processing 패키지의 스케줄 설정에 우연히 의존하지 않게 한다. */
@Configuration
@EnableScheduling
public class BlockchainLiveConfig {}
