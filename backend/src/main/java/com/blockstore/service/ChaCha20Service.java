package com.blockstore.service;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * ChaCha20-Poly1305 authenticated encryption service.
 *
 * Uses the JDK 11+ built-in "ChaCha20-Poly1305" JCA cipher.
 * Wire format: [12-byte nonce][ciphertext + 16-byte Poly1305 auth tag]
 *
 * Authentication is enforced automatically — AEADBadTagException is thrown
 * on decrypt if the ciphertext has been tampered with.
 */
@Service
public class ChaCha20Service {

    private static final String ALGORITHM = "ChaCha20-Poly1305";
    private static final String KEY_ALGORITHM = "ChaCha20";
    private static final int NONCE_SIZE = 12; // 96-bit nonce

    /** Generate a new cryptographically-random 256-bit ChaCha20 key. */
    public SecretKey generateKey() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance(KEY_ALGORITHM);
        kg.init(256, new SecureRandom());
        return kg.generateKey();
    }

    /**
     * Encrypt data with the given key.
     * 
     * @return [12-byte nonce][ciphertext + 16-byte Poly1305 tag]
     */
    public byte[] encrypt(byte[] data, SecretKey key) throws Exception {
        byte[] nonce = new byte[NONCE_SIZE];
        new SecureRandom().nextBytes(nonce);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(nonce));
        byte[] ciphertext = cipher.doFinal(data);

        byte[] result = new byte[NONCE_SIZE + ciphertext.length];
        System.arraycopy(nonce, 0, result, 0, NONCE_SIZE);
        System.arraycopy(ciphertext, 0, result, NONCE_SIZE, ciphertext.length);

        System.out.println("ChaCha20-Poly1305 Encryption done. Output size: " + result.length + " bytes");
        return result;
    }

    /**
     * Decrypt data produced by encrypt().
     * Extracts the 12-byte nonce from the front of encryptedData.
     * Throws AEADBadTagException if authentication fails.
     */
    public byte[] decrypt(byte[] encryptedData, SecretKey key) throws Exception {
        if (encryptedData.length < NONCE_SIZE) {
            throw new IllegalArgumentException("Payload too short to contain a valid nonce.");
        }

        byte[] nonce = new byte[NONCE_SIZE];
        System.arraycopy(encryptedData, 0, nonce, 0, NONCE_SIZE);

        byte[] ciphertext = new byte[encryptedData.length - NONCE_SIZE];
        System.arraycopy(encryptedData, NONCE_SIZE, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(nonce));

        byte[] decrypted = cipher.doFinal(ciphertext);
        System.out.println("🔓 ChaCha20-Poly1305 Decryption done. Size: " + decrypted.length + " bytes");
        return decrypted;
    }

    /** Encode a SecretKey to a Base64 string for on-chain storage. */
    public String keyToString(SecretKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /** Restore a SecretKey from its Base64 string representation. */
    public SecretKey stringToKey(String keyString) {
        byte[] decoded = Base64.getDecoder().decode(keyString);
        return new SecretKeySpec(decoded, KEY_ALGORITHM);
    }
}
