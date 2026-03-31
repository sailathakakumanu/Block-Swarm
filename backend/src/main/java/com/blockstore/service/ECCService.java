package com.blockstore.service;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import java.security.*;

@Service
public class ECCService {

    public ECCService() {
        // Add BouncyCastle as a security provider
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Generates a new ECC Key Pair (Public and Private keys).
     */
    public KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC", "BC");
        keyGen.initialize(256, new SecureRandom()); // Use a standard 256-bit elliptic curve
        return keyGen.generateKeyPair();
    }

    /**
     * Encrypts the ChaCha20 Symmetric Key using the User's Public ECC Key via ECIES.
     */
    public byte[] encryptSymmetricKey(byte[] chachaKeyStr, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance("ECIES", "BC");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(chachaKeyStr);
    }

    /**
     * Decrypts the ChaCha20 Symmetric Key using the User's Private ECC Key via ECIES.
     */
    public byte[] decryptSymmetricKey(byte[] encryptedChachaKey, PrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance("ECIES", "BC");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(encryptedChachaKey);
    }
}
