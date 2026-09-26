package com.ssafy.upcycle.blockchain.service;

import com.ssafy.upcycle.blockchain.config.BlockchainProperties;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.user.CompanyRoleApprovalErrorCode;
import com.ssafy.upcycle.user.entity.UserRole;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

/**
 * Frontend에서 관리자 MetaMask가 부여한 역할을 서버의 신뢰된 RPC와 컨트랙트 주소로 재검증한다.
 * Backend는 관리자 개인키를 보유하거나 트랜잭션을 대신 서명하지 않는다.
 */
@Component
public class Web3jOnChainRoleVerificationGateway implements OnChainRoleVerificationGateway {
    private final BlockchainProperties properties;

    public Web3jOnChainRoleVerificationGateway(BlockchainProperties properties) {
        this.properties = properties;
    }

    @Override
    public void verifyGranted(UserRole role, String walletAddress, List<String> transactionHashes) {
        if (!properties.hasRpcUrl()
                || properties.chainId() == null
                || properties.chainId() <= 0) {
            throw new BusinessException(CompanyRoleApprovalErrorCode.BLOCKCHAIN_NOT_CONFIGURED);
        }

        try (Web3j web3j = Web3j.build(new HttpService(properties.rpcUrl()))) {
            List<RoleTarget> targets = targets(role);
            for (String hash : transactionHashes) {
                var receipt = web3j.ethGetTransactionReceipt(hash).send()
                        .getTransactionReceipt()
                        .orElseThrow(() -> new IllegalStateException("transaction receipt not found"));
                if (!receipt.isStatusOK()) {
                    throw new IllegalStateException("role grant transaction reverted");
                }
                if (receipt.getLogs().stream().noneMatch(log ->
                        isExpectedRoleGranted(log.getAddress(), log.getTopics(), targets, walletAddress))) {
                    throw new IllegalStateException("transaction is not a matching RoleGranted event");
                }
            }

            for (RoleTarget target : targets) {
                byte[] roleHash = Numeric.hexStringToByteArray(Hash.sha3String(target.roleName()));
                if (!hasRole(web3j, target.contractAddress(), roleHash, walletAddress)) {
                    throw new IllegalStateException("required role is not granted");
                }
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(CompanyRoleApprovalErrorCode.BLOCKCHAIN_ROLE_VERIFICATION_FAILED);
        }
    }

    private boolean isExpectedRoleGranted(
            String contractAddress,
            List<String> topics,
            List<RoleTarget> targets,
            String walletAddress) {
        if (topics.size() < 3
                || !topics.get(0).equalsIgnoreCase(Hash.sha3String("RoleGranted(bytes32,address,address)"))) {
            return false;
        }
        String walletTopic = "0x" + "0".repeat(24)
                + walletAddress.substring(2).toLowerCase(Locale.ROOT);
        if (!topics.get(2).equalsIgnoreCase(walletTopic)) return false;

        return targets.stream().anyMatch(target -> {
            String roleTopic = Hash.sha3String(target.roleName());
            return target.contractAddress().equalsIgnoreCase(contractAddress)
                    && roleTopic.equalsIgnoreCase(topics.get(1));
        });
    }

    private boolean hasRole(Web3j web3j, String contract, byte[] roleHash, String wallet)
            throws Exception {
        Function function = new Function(
                "hasRole",
                List.of(new Bytes32(roleHash), new Address(wallet)),
                List.of(new TypeReference<Bool>() {}));
        EthCall call = web3j.ethCall(
                Transaction.createEthCallTransaction(
                        wallet, contract, FunctionEncoder.encode(function)),
                DefaultBlockParameterName.LATEST).send();
        if (call.hasError()) throw new IllegalStateException(call.getError().getMessage());
        var decoded = FunctionReturnDecoder.decode(call.getValue(), function.getOutputParameters());
        return !decoded.isEmpty() && ((Bool) decoded.get(0)).getValue();
    }

    private List<RoleTarget> targets(UserRole role) {
        return switch (role) {
            case SUPPLIER -> List.of(
                    new RoleTarget(required(properties.materialTokenAddress()), "SUPPLIER_ROLE"));
            case BRAND -> List.of(
                    new RoleTarget(required(properties.materialTokenAddress()), "BRAND_ROLE"),
                    new RoleTarget(required(properties.processingRegistryAddress()), "BRAND_ROLE"),
                    new RoleTarget(required(properties.productionRegistryAddress()), "BRAND_ROLE"));
            case PROCESSOR -> List.of(
                    new RoleTarget(required(properties.materialTokenAddress()), "PROCESSOR_ROLE"),
                    new RoleTarget(required(properties.processingRegistryAddress()), "PROCESSOR_ROLE"));
            default -> throw new BusinessException(CompanyRoleApprovalErrorCode.COMPANY_NOT_FOUND);
        };
    }

    private String required(String address) {
        if (address == null || address.length() != 42 || !address.startsWith("0x")) {
            throw new BusinessException(CompanyRoleApprovalErrorCode.BLOCKCHAIN_NOT_CONFIGURED);
        }
        return address;
    }

    private record RoleTarget(String contractAddress, String roleName) {}
}
