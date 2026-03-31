package com.blockstore.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Configuration
public class Web3jConfig {

    @Value("${ganache.rpc.url}")
    private String rpcUrl;

    @Value("${ganache.private.key}")
    private String privateKey;

    @Bean
    public Web3j web3j() {
        Web3j web3j = Web3j.build(new HttpService(rpcUrl));
        System.out.println("✅ Connected to Ganache at: " + rpcUrl);
        return web3j;
    }

    @Bean
    public Credentials credentials() {
        Credentials creds = Credentials.create(privateKey);
        System.out.println("✅ Wallet Address: " + creds.getAddress());
        return creds;
    }
}