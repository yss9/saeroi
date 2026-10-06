package com.ssafy.upcycle.blockchain.dto;

import com.ssafy.upcycle.blockchain.entity.BlockchainContract;
import com.ssafy.upcycle.blockchain.entity.BlockchainReferenceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record BlockchainOperationCreateRequest(
        @NotNull BlockchainContract contract,
        @NotBlank @Size(max = 100) String functionName,
        @NotNull BlockchainReferenceType referenceType,
        @NotNull @Positive Long referenceId) {}
