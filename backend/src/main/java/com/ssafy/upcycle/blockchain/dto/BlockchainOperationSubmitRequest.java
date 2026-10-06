package com.ssafy.upcycle.blockchain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BlockchainOperationSubmitRequest(
        @NotBlank @Pattern(regexp = "^0x[0-9a-fA-F]{64}$") String txHash) {}
