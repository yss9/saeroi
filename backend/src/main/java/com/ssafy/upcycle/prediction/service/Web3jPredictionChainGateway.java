package com.ssafy.upcycle.prediction.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.prediction.PredictionErrorCode;
import com.ssafy.upcycle.prediction.config.PredictionProperties;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import org.web3j.utils.Numeric;

@Component
public class Web3jPredictionChainGateway implements PredictionChainGateway {
    private static final BigInteger GAS_LIMIT = BigInteger.valueOf(1_500_000);
    private static final String MARKET_CREATED_TOPIC = Hash.sha3String("MarketCreated(uint256,uint256,uint256,address,uint256,uint256,uint64,uint64,uint64)");
    private static final String BET_PLACED_TOPIC = Hash.sha3String("BetPlaced(uint256,address,uint256,uint256)");
    private final PredictionProperties properties;
    private final Object writeLock = new Object();

    public Web3jPredictionChainGateway(PredictionProperties properties) { this.properties=properties; }

    @Override
    public MarketCreated createMarket(long batchId,long productModelId,long unitPrice,LocalDateTime bettingEndsAt,LocalDateTime salesStartsAt,LocalDateTime salesEndsAt,List<Integer> bounds) {
        List<Uint256> encodedBounds=bounds.stream().map(value -> new Uint256(BigInteger.valueOf(value))).toList();
        Function function=new Function("createMarket",List.of(
                new Uint256(BigInteger.valueOf(batchId)),new Uint256(BigInteger.valueOf(productModelId)),new Uint256(BigInteger.valueOf(unitPrice)),
                new Uint64(epoch(bettingEndsAt)),new Uint64(epoch(salesStartsAt)),new Uint64(epoch(salesEndsAt)),new DynamicArray<>(Uint256.class,encodedBounds)),List.of());
        TransactionReceipt receipt=send(properties.marketAddress(),function);
        for(Log log:receipt.getLogs()) if(log.getAddress().equalsIgnoreCase(properties.marketAddress()) && log.getTopics().size()>1 && log.getTopics().get(0).equalsIgnoreCase(MARKET_CREATED_TOPIC))
            return new MarketCreated(Numeric.toBigInt(log.getTopics().get(1)).longValueExact(),receipt.getTransactionHash());
        throw failed();
    }

    @Override public String submitResult(long marketId,int sold,String hash){
        Function function=new Function("submitResult",List.of(new Uint256(BigInteger.valueOf(marketId)),new Uint256(BigInteger.valueOf(sold)),new org.web3j.abi.datatypes.generated.Bytes32(Numeric.hexStringToByteArray(hash))),List.of());
        return send(properties.marketAddress(),function).getTransactionHash();
    }

    @Override public String mintPoint(String wallet,BigInteger amount){
        Function function=new Function("mint",List.of(new Address(wallet),new Uint256(amount)),List.of());
        return send(properties.tokenAddress(),function).getTransactionHash();
    }

    @Override
    public boolean verifyBet(String txHash,long marketId,String wallet,int bucket,BigInteger amount){
        requireReadable();
        try(Web3j web3j=Web3j.build(new HttpService(properties.rpcUrl()))){
            var response=web3j.ethGetTransactionReceipt(txHash).send();
            if(response.hasError()||response.getTransactionReceipt().isEmpty()) return false;
            TransactionReceipt receipt=response.getTransactionReceipt().orElseThrow();
            if(!receipt.isStatusOK()||!receipt.getFrom().equalsIgnoreCase(wallet)) return false;
            for(Log log:receipt.getLogs()){
                List<String> topics=log.getTopics();
                if(log.getAddress().equalsIgnoreCase(properties.marketAddress())&&topics.size()==4&&topics.get(0).equalsIgnoreCase(BET_PLACED_TOPIC)
                        && Numeric.toBigInt(topics.get(1)).longValue()==marketId
                        && topicAddress(topics.get(2)).equalsIgnoreCase(wallet)
                        && Numeric.toBigInt(topics.get(3)).intValue()==bucket
                        && Numeric.toBigInt(log.getData()).equals(amount)) return true;
            }
            return false;
        }catch(Exception exception){throw failed();}
    }

    private TransactionReceipt send(String to,Function function){
        if(!properties.canWrite()) throw new BusinessException(PredictionErrorCode.NOT_CONFIGURED);
        synchronized(writeLock){
            try(Web3j web3j=Web3j.build(new HttpService(properties.rpcUrl()))){
                Credentials credentials=Credentials.create(properties.operatorPrivateKey());
                RawTransactionManager manager=new RawTransactionManager(web3j,credentials,properties.chainId());
                BigInteger gasPrice=web3j.ethGasPrice().send().getGasPrice();
                EthSendTransaction sent=manager.sendTransaction(gasPrice,GAS_LIMIT,to,FunctionEncoder.encode(function),BigInteger.ZERO);
                if(sent.hasError()) throw new IllegalStateException(sent.getError().getMessage());
                TransactionReceipt receipt=new PollingTransactionReceiptProcessor(web3j,1_000,90).waitForTransactionReceipt(sent.getTransactionHash());
                if(!receipt.isStatusOK()) throw new IllegalStateException("transaction reverted");
                return receipt;
            }catch(BusinessException exception){throw exception;}catch(Exception exception){throw failed();}
        }
    }

    private void requireReadable(){if(!properties.isReadable()) throw new BusinessException(PredictionErrorCode.NOT_CONFIGURED);}
    private static BigInteger epoch(LocalDateTime value){return BigInteger.valueOf(value.toEpochSecond(ZoneOffset.UTC));}
    private static String topicAddress(String topic){String hex=Numeric.cleanHexPrefix(topic);return "0x"+hex.substring(hex.length()-40);}
    private static BusinessException failed(){return new BusinessException(PredictionErrorCode.CHAIN_TRANSACTION_FAILED);}
}
