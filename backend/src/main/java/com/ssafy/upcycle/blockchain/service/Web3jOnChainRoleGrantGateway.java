package com.ssafy.upcycle.blockchain.service;

import com.ssafy.upcycle.blockchain.config.BlockchainProperties;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.user.CompanyRoleApprovalErrorCode;
import com.ssafy.upcycle.user.entity.UserRole;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import org.web3j.utils.Numeric;

@Component
public class Web3jOnChainRoleGrantGateway implements OnChainRoleGrantGateway {
    private static final BigInteger GAS_LIMIT = BigInteger.valueOf(500_000);
    private final BlockchainProperties properties;

    public Web3jOnChainRoleGrantGateway(BlockchainProperties properties) {
        this.properties = properties;
    }

    @Override
    public RoleGrantResult grant(UserRole role, String walletAddress) {
        if (!properties.hasAdminSigner() || !properties.isConfigured()) {
            throw new BusinessException(CompanyRoleApprovalErrorCode.BLOCKCHAIN_ADMIN_NOT_CONFIGURED);
        }

        try (Web3j web3j = Web3j.build(new HttpService(properties.rpcUrl()))) {
            Credentials credentials = Credentials.create(properties.adminPrivateKey());
            RawTransactionManager sender = new RawTransactionManager(
                    web3j, credentials, properties.chainId());
            PollingTransactionReceiptProcessor receipts =
                    new PollingTransactionReceiptProcessor(web3j, 1_000, 60);
            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
            List<String> hashes = new ArrayList<>();

            for (RoleTarget target : targets(role)) {
                byte[] roleHash = Numeric.hexStringToByteArray(Hash.sha3String(target.roleName()));
                if (hasRole(web3j, credentials.getAddress(), target.contractAddress(), roleHash, walletAddress)) {
                    continue;
                }
                Function grantRole = new Function(
                        "grantRole",
                        List.of(new Bytes32(roleHash), new Address(walletAddress)),
                        List.of());
                EthSendTransaction submitted = sender.sendTransaction(
                        gasPrice, GAS_LIMIT, target.contractAddress(),
                        FunctionEncoder.encode(grantRole), BigInteger.ZERO);
                if (submitted.hasError()) throw new IllegalStateException(submitted.getError().getMessage());
                TransactionReceipt receipt = receipts.waitForTransactionReceipt(submitted.getTransactionHash());
                if (!receipt.isStatusOK()) throw new IllegalStateException("role grant reverted");
                hashes.add(receipt.getTransactionHash());
            }
            return new RoleGrantResult(hashes);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(CompanyRoleApprovalErrorCode.BLOCKCHAIN_ROLE_GRANT_FAILED);
        }
    }

    private boolean hasRole(
            Web3j web3j, String from, String contract, byte[] roleHash, String wallet) throws Exception {
        Function function = new Function(
                "hasRole",
                List.of(new Bytes32(roleHash), new Address(wallet)),
                List.of(new TypeReference<Bool>() {}));
        EthCall call = web3j.ethCall(
                Transaction.createEthCallTransaction(from, contract, FunctionEncoder.encode(function)),
                DefaultBlockParameterName.LATEST).send();
        if (call.hasError()) throw new IllegalStateException(call.getError().getMessage());
        var decoded = FunctionReturnDecoder.decode(call.getValue(), function.getOutputParameters());
        return !decoded.isEmpty() && ((Bool) decoded.get(0)).getValue();
    }

    private List<RoleTarget> targets(UserRole role) {
        return switch (role) {
            case SUPPLIER -> List.of(new RoleTarget(properties.materialTokenAddress(), "SUPPLIER_ROLE"));
            case BRAND -> List.of(
                    new RoleTarget(properties.materialTokenAddress(), "BRAND_ROLE"),
                    new RoleTarget(required(properties.processingRegistryAddress()), "BRAND_ROLE"),
                    new RoleTarget(required(properties.productionRegistryAddress()), "BRAND_ROLE"));
            case PROCESSOR -> List.of(
                    new RoleTarget(properties.materialTokenAddress(), "PROCESSOR_ROLE"),
                    new RoleTarget(required(properties.processingRegistryAddress()), "PROCESSOR_ROLE"));
            default -> throw new BusinessException(CompanyRoleApprovalErrorCode.COMPANY_NOT_FOUND);
        };
    }

    private String required(String address) {
        if (address == null || address.length() != 42 || !address.startsWith("0x")) {
            throw new BusinessException(CompanyRoleApprovalErrorCode.BLOCKCHAIN_ADMIN_NOT_CONFIGURED);
        }
        return address;
    }

    private record RoleTarget(String contractAddress, String roleName) {}
}
