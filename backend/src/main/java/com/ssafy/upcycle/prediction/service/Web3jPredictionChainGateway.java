package com.ssafy.upcycle.prediction.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.prediction.PredictionErrorCode;
import com.ssafy.upcycle.prediction.config.PredictionProperties;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import org.web3j.utils.Numeric;

@Slf4j
@Component
public class Web3jPredictionChainGateway implements PredictionChainGateway {
    private static final BigInteger GAS_LIMIT = BigInteger.valueOf(1_500_000);
    /** Solidity {@code Error(string)} 의 선택자. require 문구가 이 뒤에 ABI 로 실린다. */
    private static final String ERROR_STRING_SELECTOR = "0x08c379a0";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
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

    @Override public String cancelMarket(long marketId){
        Function function=new Function("cancelMarket",List.of(new Uint256(BigInteger.valueOf(marketId))),List.of());
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
                String data=FunctionEncoder.encode(function);
                // 보내기 전에 같은 호출을 eth_call 로 흉내 낸다. 되돌려질 거래를 보내면 사유는 영수증에
                // 남지 않고(status=0x0 뿐) 블록만 쌓인다 — 여기서 require 문구를 받아 남기고 멈춘다.
                EthCall dryRun=web3j.ethCall(Transaction.createEthCallTransaction(credentials.getAddress(),to,data),DefaultBlockParameterName.LATEST).send();
                String reason=revertReason(dryRun);
                if(reason!=null){
                    log.warn("Prediction chain call would revert: function={} reason={}",function.getName(),reason);
                    throw reverted(reason);
                }
                RawTransactionManager manager=new RawTransactionManager(web3j,credentials,properties.chainId());
                BigInteger gasPrice=web3j.ethGasPrice().send().getGasPrice();
                EthSendTransaction sent=manager.sendTransaction(gasPrice,GAS_LIMIT,to,data,BigInteger.ZERO);
                if(sent.hasError()) throw new IllegalStateException(sent.getError().getMessage());
                TransactionReceipt receipt=new PollingTransactionReceiptProcessor(web3j,1_000,90).waitForTransactionReceipt(sent.getTransactionHash());
                if(!receipt.isStatusOK()) throw new IllegalStateException("transaction reverted: "+receipt.getTransactionHash());
                return receipt;
            }catch(BusinessException exception){throw exception;}catch(Exception exception){
                // 사유를 숨기면 운영에서 원인을 찾을 길이 없다. 키는 메시지에 실리지 않는다.
                log.warn("Prediction chain transaction failed: function={} reason={}: {}",function.getName(),exception.getClass().getSimpleName(),exception.getMessage());
                throw failed();
            }
        }
    }

    /** 사전 호출이 되돌려졌다면 그 사유. 통과했으면 null. */
    static String revertReason(EthCall call){
        if(call.hasError()){
            String decoded=decodeErrorString(call.getError().getData());
            return decoded!=null?decoded:call.getError().getMessage();
        }
        if(call.isReverted()) return call.getRevertReason()!=null?call.getRevertReason():"reverted";
        return null;
    }

    /** {@code Error(string)} 로 인코딩된 require 문구를 푼다. 그 모양이 아니면 null. */
    static String decodeErrorString(String data){
        if(data==null) return null;
        String hex=data.trim().replace("\"","");
        if(!hex.toLowerCase().startsWith(ERROR_STRING_SELECTOR)) return null;
        try{
            var decoded=FunctionReturnDecoder.decode("0x"+hex.substring(ERROR_STRING_SELECTOR.length()),org.web3j.abi.Utils.convert(List.of(new TypeReference<Utf8String>(){})));
            return decoded.isEmpty()?null:decoded.get(0).getValue().toString();
        }catch(RuntimeException malformed){return null;}
    }

    private static BusinessException reverted(String reason){
        return reason.contains("batch is not confirmed")
                ? new BusinessException(PredictionErrorCode.BATCH_NOT_CONFIRMED_ON_CHAIN)
                : failed();
    }

    private void requireReadable(){if(!properties.isReadable()) throw new BusinessException(PredictionErrorCode.NOT_CONFIGURED);}
    static BigInteger epoch(LocalDateTime value){return BigInteger.valueOf(value.atZone(BUSINESS_ZONE).toEpochSecond());}
    private static String topicAddress(String topic){String hex=Numeric.cleanHexPrefix(topic);return "0x"+hex.substring(hex.length()-40);}
    private static BusinessException failed(){return new BusinessException(PredictionErrorCode.CHAIN_TRANSACTION_FAILED);}
}
