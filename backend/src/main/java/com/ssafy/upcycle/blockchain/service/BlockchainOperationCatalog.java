package com.ssafy.upcycle.blockchain.service;

import com.ssafy.upcycle.blockchain.entity.BlockchainContract;
import java.util.Map;
import org.web3j.crypto.Hash;

final class BlockchainOperationCatalog {
    private static final Map<BlockchainContract, Map<String, String>> SIGNATURES = Map.of(
            BlockchainContract.MATERIAL_TOKEN, Map.of(
                    "mintMaterial", "mintMaterial(address,uint256,uint8,string,bytes32,bytes32)",
                    "verifySource", "verifySource(uint256,bytes32,bytes32)",
                    "verifyTransaction", "verifyTransaction(uint256,uint256,bytes32,bytes32)",
                    "burnMaterial", "burnMaterial(address,uint256,uint256,uint8,bytes32)"),
            BlockchainContract.MARKETPLACE, Map.of(
                    "createListing", "createListing(uint256,uint256,bytes32)",
                    "cancelListing", "cancelListing(uint256,bytes32)",
                    "createOrder", "createOrder(uint256,uint256,bytes32)",
                    "markShipped", "markShipped(uint256,bytes32)",
                    "confirmReceipt", "confirmReceipt(uint256,bytes32)",
                    "cancelOrder", "cancelOrder(uint256,bytes32)",
                    "openDispute", "openDispute(uint256,bytes32,bytes32)",
                    "settleDispute", "settleDispute(uint256,uint8,bytes32)"),
            BlockchainContract.PROCESSING_REGISTRY, Map.ofEntries(
                    Map.entry("createJob", "createJob(address,uint256[],uint256[],uint8[],bytes32)"),
                    Map.entry("acceptJob", "acceptJob(uint256,bytes32)"),
                    Map.entry("rejectJob", "rejectJob(uint256,bytes32)"),
                    Map.entry("cancelJob", "cancelJob(uint256,bytes32)"),
                    Map.entry("markShipped", "markShipped(uint256,bytes32,bytes32)"),
                    Map.entry("confirmReceipt", "confirmReceipt(uint256,bytes32)"),
                    Map.entry("startProcessing", "startProcessing(uint256,bytes32)"),
                    Map.entry("submitProcessingResult", "submitProcessingResult(uint256,uint256[],uint256[],uint8[],string[],bytes32[],bytes32,bytes32)"),
                    Map.entry("markReturnShipped", "markReturnShipped(uint256,bytes32,bytes32)"),
                    Map.entry("returnRejectedInput", "returnRejectedInput(uint256,bytes32,bytes32)"),
                    Map.entry("approveProcessingResult", "approveProcessingResult(uint256,bytes32)"),
                    Map.entry("rejectProcessingResult", "rejectProcessingResult(uint256,bytes32)")),
            BlockchainContract.PRODUCTION_REGISTRY, Map.of(
                    "createBatch", "createBatch(uint256,bytes32)",
                    "submitPlanAndLock", "submitPlanAndLock(uint256,bytes32,uint256[],uint256[],uint256[],uint256[],uint256[],string[],bytes32[],bytes32)",
                    "confirmProduction", "confirmProduction(uint256,bytes32)",
                    "cancelProduction", "cancelProduction(uint256,bytes32)",
                    "markFailed", "markFailed(uint256,bytes32)",
                    "linkProductItems", "linkProductItems(uint256,uint256[],bytes32)"));

    private BlockchainOperationCatalog() {}

    static String selector(BlockchainContract contract, String functionName) {
        String signature = SIGNATURES.getOrDefault(contract, Map.of()).get(functionName);
        if (signature == null) throw new IllegalArgumentException("unsupported blockchain operation");
        return Hash.sha3String(signature).substring(0, 10).toLowerCase();
    }
}
