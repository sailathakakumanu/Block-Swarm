package com.blockstore.service;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.tx.gas.DefaultGasProvider;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

@Service
public class BlockchainService {

    private final Web3j web3j;
    private final Credentials credentials;

    @Value("${contract.address}")
    private String contractAddress;

    // ─── Inner result record ─────────────────────────────────────────────────

    /** Carries the on-chain transaction hash and confirmed block number. */
    public static class TxResult {
        public final String txHash;
        public final long blockNumber;

        public TxResult(String txHash, long blockNumber) {
            this.txHash = txHash;
            this.blockNumber = blockNumber;
        }
    }

    public BlockchainService(Web3j web3j, Credentials credentials) {
        this.web3j = web3j;
        this.credentials = credentials;
    }

    @PostConstruct
    public void init() {
        try {
            validateContractExists();
            System.out.println("🔗 Connected to Blockchain & verified Smart Contract at: " + contractAddress);
        } catch (Exception e) {
            System.err.println("\n🔥\n🔥 " + e.getMessage() + "\n🔥\n");
            throw new RuntimeException("Smart contract validation failed on startup: " + contractAddress, e);
        }
    }

    // ─── Core helpers ────────────────────────────────────────────────────────

    private TxResult sendTransaction(Function function) throws Exception {
        String encodedFunction = FunctionEncoder.encode(function);

        BigInteger nonce = web3j
                .ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.LATEST)
                .send()
                .getTransactionCount();

        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();

        // Gas estimation with 20% buffer
        BigInteger estimatedGas;
        try {
            estimatedGas = web3j.ethEstimateGas(
                    Transaction.createFunctionCallTransaction(
                            credentials.getAddress(), nonce, gasPrice,
                            BigInteger.valueOf(3_000_000), contractAddress, encodedFunction))
                    .send().getAmountUsed();
            estimatedGas = estimatedGas.multiply(BigInteger.valueOf(120)).divide(BigInteger.valueOf(100));
        } catch (Exception e) {
            estimatedGas = BigInteger.valueOf(3_000_000); // Safe fallback for Ganache
        }

        Transaction tx = Transaction.createFunctionCallTransaction(
                credentials.getAddress(), nonce, gasPrice, estimatedGas,
                contractAddress, encodedFunction);

        EthSendTransaction sent = web3j.ethSendTransaction(tx).send();
        if (sent.hasError()) {
            System.err.println("🛑 Web3j TX Error: " + sent.getError().getMessage());
            throw new RuntimeException("❌ TX Error: " + sent.getError().getMessage());
        }

        String txHash = sent.getTransactionHash();
        System.out.println("📦 TX Sent. Hash: " + txHash);

        Thread.sleep(2000); // Wait for Ganache

        final long[] blockNum = { -1 };
        final boolean[] isReverted = { false };
        web3j.ethGetTransactionReceipt(txHash).send()
                .getTransactionReceipt().ifPresentOrElse(r -> {
                    blockNum[0] = r.getBlockNumber().longValue();
                    System.out.println("✅ TX Mined in block: " + r.getBlockNumber());
                    if (!r.isStatusOK()) {
                        System.err.println("⚠️  TX REVERTED on-chain!");
                        isReverted[0] = true;
                    }
                }, () -> {
                    System.err.println("⏳ TX still pending or not found in block.");
                });

        if (isReverted[0]) {
            throw new RuntimeException("Transaction reverted on the blockchain. Check smart contract restrictions.");
        }

        return new TxResult(txHash, blockNum[0]);
    }

    /** 
     * Validates that the contract exists at the configured address.
     * Web3j will silently succeed sending TXs to empty addresses, which causes state to be lost.
     */
    public void validateContractExists() throws Exception {
        String code = web3j.ethGetCode(contractAddress, DefaultBlockParameterName.LATEST).send().getCode();
        if (code == null || code.equals("0x")) {
            throw new RuntimeException("CRITICAL: No smart contract found at address " + contractAddress + 
                ". Did you restart Ganache without redeploying FileStorage.sol? Please deploy the contract in Remix and update application.properties.");
        }
    }

    // ─── storeFile ───────────────────────────────────────────────────────────

    public TxResult storeFileMetadata(String fileHash, String encryptedKey) throws Exception {
        Function function = new Function(
                "storeFile",
                Arrays.asList(new Utf8String(fileHash), new Utf8String(encryptedKey)),
                List.of());
        TxResult result = sendTransaction(function);
        System.out.println("📂 storeFile complete — block #" + result.blockNumber);
        return result;
    }

    // ─── addOwner ────────────────────────────────────────────────────────────

    public TxResult addOwner(String fileHash, String ownerAddress) throws Exception {
        Function function = new Function(
                "addOwner",
                Arrays.asList(new Utf8String(fileHash), new Address(ownerAddress)),
                List.of());
        TxResult result = sendTransaction(function);
        System.out.println("👤 addOwner complete — block #" + result.blockNumber);
        return result;
    }

    // ─── fileExists ──────────────────────────────────────────────────────────

    public boolean fileExistsOnBlockchain(String fileHash) throws Exception {
        Function function = new Function(
                "fileExists",
                List.of(new Utf8String(fileHash)),
                List.of(new TypeReference<Bool>() {
                }));

        EthCall response = web3j.ethCall(
                Transaction.createEthCallTransaction(
                        credentials.getAddress(), contractAddress, FunctionEncoder.encode(function)),
                DefaultBlockParameterName.LATEST).send();

        List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
        if (decoded.isEmpty())
            return false;
        boolean exists = (boolean) decoded.get(0).getValue();
        System.out.println("🔍 fileExists on blockchain: " + exists);
        return exists;
    }

    // ─── isOwner ─────────────────────────────────────────────────────────────

    public boolean isOwner(String fileHash, String callerAddress) throws Exception {
        Function function = new Function(
                "isOwner",
                Arrays.asList(new Utf8String(fileHash), new Address(callerAddress)),
                List.of(new TypeReference<Bool>() {
                }));

        EthCall response = web3j.ethCall(
                Transaction.createEthCallTransaction(
                        credentials.getAddress(), contractAddress, FunctionEncoder.encode(function)),
                DefaultBlockParameterName.LATEST).send();

        List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
        if (decoded.isEmpty())
            return false;
        boolean owner = (boolean) decoded.get(0).getValue();
        System.out.println("🔐 isOwner [" + callerAddress + "]: " + owner);
        return owner;
    }

    // ─── emitFileAccessed ────────────────────────────────────────────────────

    public TxResult emitFileAccessed(String fileHash, String callerAddress) throws Exception {
        Function function = new Function(
                "emitAccess",
                Arrays.asList(new Utf8String(fileHash), new Address(callerAddress)),
                List.of());
        TxResult result = sendTransaction(function);
        System.out.println("📡 FileAccessed event emitted — block #" + result.blockNumber);
        return result;
    }

    // ─── getFileMetadata ─────────────────────────────────────────────────────

    public String[] getFileMetadata(String fileHash) throws Exception {
        Function function = new Function(
                "getFile",
                List.of(new Utf8String(fileHash)),
                Arrays.asList(
                        new TypeReference<Utf8String>() {
                        },
                        new TypeReference<Utf8String>() {
                        },
                        new TypeReference<Address>() {
                        },
                        new TypeReference<Uint256>() {
                        }));

        EthCall response = web3j.ethCall(
                Transaction.createEthCallTransaction(
                        credentials.getAddress(), contractAddress, FunctionEncoder.encode(function)),
                DefaultBlockParameterName.LATEST).send();

        List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
        if (decoded == null || decoded.isEmpty()) {
            return null; // Return null if contract returns empty data
        }
        return new String[] {
                decoded.get(0).getValue().toString(),
                decoded.get(1).getValue().toString(),
                decoded.get(2).getValue().toString(),
                decoded.get(3).getValue().toString()
        };
    }

    /**
     * Queries the latest N blocks from Ganache and returns a list of
     * transaction summaries (txHash, blockNumber, from, to).
     */
    public List<java.util.Map<String, Object>> getRecentTransactions(int count) throws Exception {
        List<java.util.Map<String, Object>> txList = new java.util.ArrayList<>();

        BigInteger latestBlock = web3j.ethBlockNumber().send().getBlockNumber();
        BigInteger start = latestBlock.subtract(BigInteger.valueOf(count - 1));
        if (start.compareTo(BigInteger.ZERO) < 0)
            start = BigInteger.ZERO;

        for (BigInteger i = latestBlock; i.compareTo(start) >= 0; i = i.subtract(BigInteger.ONE)) {
            org.web3j.protocol.core.methods.response.EthBlock block = web3j.ethGetBlockByNumber(
                    org.web3j.protocol.core.DefaultBlockParameter.valueOf(i), true).send();

            if (block.getBlock() == null)
                continue;

            for (org.web3j.protocol.core.methods.response.EthBlock.TransactionResult<?> txResult : block.getBlock()
                    .getTransactions()) {
                org.web3j.protocol.core.methods.response.EthBlock.TransactionObject tx = (org.web3j.protocol.core.methods.response.EthBlock.TransactionObject) txResult
                        .get();

                java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
                entry.put("txHash", tx.getHash());
                entry.put("blockNumber", tx.getBlockNumber().longValue());
                entry.put("from", tx.getFrom());
                entry.put("to", tx.getTo() != null ? tx.getTo() : "Contract Creation");
                txList.add(entry);
            }
        }
        return txList;
    }

    /** Returns the default wallet address for ownership checks. */
    public String getDefaultAddress() {
        return credentials.getAddress();
    }
}
