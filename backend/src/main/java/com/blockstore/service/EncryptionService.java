package com.blockstore.service;

import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.util.Base64;

/**
 * Encryption facade — replaces AES with ChaCha20-Poly1305.
 *
 * Manages per-file key generation and delegates all cipher operations
 * to ChaCha20Service. SHA-256 hashing is kept unchanged.
 */
@Service
public class EncryptionService {

    private final ChaCha20Service chaCha20Service;
    private final ECCService eccService;

    /** Per-upload key stored in ThreadLocal for safety in concurrent scenarios. */
    private final ThreadLocal<SecretKey> currentKey = new ThreadLocal<>();
    
    /** Per-upload ECC Encrypted key stored in ThreadLocal. */
    private final ThreadLocal<String> currentEncryptedKey = new ThreadLocal<>();

    public EncryptionService(ChaCha20Service chaCha20Service, ECCService eccService) {
        this.chaCha20Service = chaCha20Service;
        this.eccService = eccService;
    }

    /**
     * Generate a fresh per-file ChaCha20 key and encrypt the given bytes.
     * The generated key is stored in currentKey for later retrieval.
     */
    public byte[] encryptFile(byte[] fileBytes) throws Exception {
        SecretKey key = chaCha20Service.generateKey();
        currentKey.set(key);
        return chaCha20Service.encrypt(fileBytes, key);
    }

    /**
     * Decrypt file bytes using the provided Base64-encoded key string (from
     * blockchain).
     */
    public byte[] decryptFile(byte[] encryptedBytes, String keyString) throws Exception {
        SecretKey key = chaCha20Service.stringToKey(keyString);
        return chaCha20Service.decrypt(encryptedBytes, key);
    }

    /**
     * Returns the Base64-encoded current encryption key (set by the most recent
     * encryptFile call).
     * This value is stored on-chain alongside the file hash.
     */
    public String getEncryptedKeyAsString() {
        SecretKey key = currentKey.get();
        if (key == null)
            throw new IllegalStateException("No encryption key available — call encryptFile() first.");
        return chaCha20Service.keyToString(key);
    }

    /**
     * SHA-256 hash of raw bytes returned as lowercase hex string.
     * Used for content-based deduplication. UNCHANGED from original.
     */
    public String generateSHA256Hash(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(data);
        StringBuilder hex = new StringBuilder();
        for (byte b : hashBytes) {
            hex.append(String.format("%02x", b));
        }
        String hash = hex.toString();
        System.out.println("🔑 SHA-256 Hash: " + hash);
        return hash;
    }

    /**
     * Hybrid Encryption: Generate ChaCha20 key, encrypt file, and encrypt the key with user's ECC public key.
     */
    public byte[] encryptFileForUser(byte[] fileBytes, java.security.PublicKey targetUserPublicKey) throws Exception {
        // 1. Generate ChaCha20 key and encrypt the file
        SecretKey chachaKey = chaCha20Service.generateKey();
        byte[] encryptedFile = chaCha20Service.encrypt(fileBytes, chachaKey);
        
        // 2. Encrypt the ChaCha20 key using ECC
        byte[] rawKeyBytes = chachaKey.getEncoded();
        String encryptedSymmetricKey = encryptKeyForUser(rawKeyBytes, targetUserPublicKey);
        
        // 3. Store encryptedSymmetricKey safely
        currentEncryptedKey.set(encryptedSymmetricKey);
        currentKey.set(chachaKey); // Keep the raw key in thread-local for subsequent sharing in the same request
        
        return encryptedFile;
    }

    /**
     * Encrypts a raw symmetric key for a specific user.
     */
    public String encryptKeyForUser(byte[] rawKeyBytes, java.security.PublicKey publicKey) throws Exception {
        byte[] encrypted = eccService.encryptSymmetricKey(rawKeyBytes, publicKey);
        return java.util.Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * Decrypts a wrapped key using a user's private key.
     */
    public SecretKey decryptKeyForUser(String encryptedKeyString, java.security.PrivateKey privateKey) throws Exception {
        byte[] encryptedSymmetricKey = java.util.Base64.getDecoder().decode(encryptedKeyString);
        byte[] rawKeyBytes = eccService.decryptSymmetricKey(encryptedSymmetricKey, privateKey);
        return new javax.crypto.spec.SecretKeySpec(rawKeyBytes, "ChaCha20");
    }

    /**
     * Retrieves the raw ChaCha20 key from thread-local (if available).
     */
    public SecretKey getCurrentRawKey() {
        return currentKey.get();
    }

    /**
     * Retrieves the Base64 ECC Encrypted Key after encryptFileForUser is called.
     */
    public String getCurrentEccEncryptedKeyAsString() {
        String key = currentEncryptedKey.get();
        if (key == null)
            throw new IllegalStateException("No ECC encryption key available — call encryptFileForUser() first.");
        return key;
    }

    /**
     * Hybrid Decryption: Decrypt the ECC-encrypted ChaCha20 key, then decrypt the file.
     */
    public byte[] decryptFileForUser(byte[] encryptedBytes, String encryptedKeyString, java.security.PrivateKey userPrivateKey) throws Exception {
        byte[] encryptedSymmetricKey = java.util.Base64.getDecoder().decode(encryptedKeyString);
        byte[] rawKeyBytes = eccService.decryptSymmetricKey(encryptedSymmetricKey, userPrivateKey);
        SecretKey key = new javax.crypto.spec.SecretKeySpec(rawKeyBytes, "ChaCha20");
        return chaCha20Service.decrypt(encryptedBytes, key);
    }
}
