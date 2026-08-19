package com.blockstore.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.*;

@Service
public class EdgeCacheService {

    @Value("${storage.edge}")
    private String edgeDir;

    public boolean isInEdgeCache(String fileHash) {
        boolean exists = Files.exists(Paths.get(edgeDir, fileHash + "_cached"));
        System.out.println("⚡ Edge Cache: " + (exists ? "HIT ✅" : "MISS "));
        return exists;
    }

    public byte[] readFromEdgeCache(String fileHash) throws Exception {
        byte[] data = Files.readAllBytes(Paths.get(edgeDir, fileHash + "_cached"));
        System.out.println("⚡ Served from edge cache: " + data.length + " bytes");
        return data;
    }

    public void writeToEdgeCache(String fileHash, byte[] encryptedData) throws Exception {
        Files.createDirectories(Paths.get(edgeDir));
        Files.write(Paths.get(edgeDir, fileHash + "_cached"), encryptedData);
        System.out.println("💾 Saved to edge cache.");
    }

    public void invalidateCache(String fileHash) {
        try {
            Files.deleteIfExists(Paths.get(edgeDir, fileHash + "_cached"));
            System.out.println("🗑 Edge cache invalidated for: " + fileHash);
        } catch (Exception e) {
            System.err.println("Could not invalidate cache: " + e.getMessage());
        }
    }
}