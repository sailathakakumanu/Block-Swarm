package com.blockstore.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.Arrays;

/**
 * StorageService — Fragment distribution across 3 storage nodes.
 *
 * Enhancements:
 * - storeFragments() also writes XOR parity fragment to edge_node
 * - reconstructWithRecovery() recovers one missing fragment via XOR parity
 *
 * All original fragmentation logic is preserved unchanged.
 */
@Service
public class StorageService {

    @Value("${storage.node1}")
    private String node1;

    @Value("${storage.node2}")
    private String node2;

    @Value("${storage.node3}")
    private String node3;

    @Value("${storage.edge}")
    private String edgeDir;

    // ─── Store ───────────────────────────────────────────────────────────────

    /**
     * Fragment encrypted data into 3 parts and store across nodes.
     * Also computes XOR parity (frag1 ⊕ frag2 ⊕ frag3) and stores in edge_node.
     */
    public void storeFragments(String fileHash, byte[] encryptedData) throws Exception {
        int total = encryptedData.length;
        int fragSize = (int) Math.ceil(total / 3.0);

        byte[] frag1 = Arrays.copyOfRange(encryptedData, 0, Math.min(fragSize, total));
        byte[] frag2 = Arrays.copyOfRange(encryptedData, Math.min(fragSize, total), Math.min(fragSize * 2, total));
        byte[] frag3 = Arrays.copyOfRange(encryptedData, Math.min(fragSize * 2, total), total);

        writeFragment(node1, fileHash + "_frag1", frag1);
        writeFragment(node2, fileHash + "_frag2", frag2);
        writeFragment(node3, fileHash + "_frag3", frag3);

        // XOR parity for fault tolerance
        byte[] parity = computeXorParity(frag1, frag2, frag3);
        writeFragment(edgeDir, fileHash + "_parity", parity);

        System.out.println("📂 Fragments stored:");
        System.out.println("   Node1 → frag1 (" + frag1.length + " bytes)");
        System.out.println("   Node2 → frag2 (" + frag2.length + " bytes)");
        System.out.println("   Node3 → frag3 (" + frag3.length + " bytes)");
        System.out.println("   Edge  → parity (" + parity.length + " bytes) [XOR of all 3]");
    }

    // ─── Reconstruct ─────────────────────────────────────────────────────────

    /**
     * Reconstruct encrypted payload from fragments.
     * Tolerates up to ONE missing fragment — recovers using XOR parity.
     */
    public byte[] reconstructWithRecovery(String fileHash) throws Exception {
        byte[] frag1 = tryReadFragment(node1, fileHash + "_frag1");
        byte[] frag2 = tryReadFragment(node2, fileHash + "_frag2");
        byte[] frag3 = tryReadFragment(node3, fileHash + "_frag3");

        int missing = 0;
        if (frag1 == null) {
            missing++;
            System.out.println("⚠️  frag1 MISSING");
        }
        if (frag2 == null) {
            missing++;
            System.out.println("⚠️  frag2 MISSING");
        }
        if (frag3 == null) {
            missing++;
            System.out.println("⚠️  frag3 MISSING");
        }

        if (missing > 1) {
            throw new RuntimeException("❌ Too many fragments missing! Cannot recover.");
        }

        if (missing == 1) {
            byte[] parity = tryReadFragment(edgeDir, fileHash + "_parity");
            if (parity == null) {
                throw new RuntimeException("❌ Parity fragment missing — cannot recover.");
            }
            System.out.println("🔧 Attempting XOR parity recovery...");
            if (frag1 == null) {
                frag1 = xorRecover(parity, frag2, frag3);
                System.out.println("✅ frag1 recovered via XOR.");
            } else if (frag2 == null) {
                frag2 = xorRecover(parity, frag1, frag3);
                System.out.println("✅ frag2 recovered via XOR.");
            } else {
                frag3 = xorRecover(parity, frag1, frag2);
                System.out.println("✅ frag3 recovered via XOR.");
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(frag1);
        out.write(frag2);
        out.write(frag3);

        System.out.println("✅ File reconstructed successfully. Total: " + out.size() + " bytes");
        return out.toByteArray();
    }

    // ─── XOR Parity Helpers ──────────────────────────────────────────────────

    private byte[] computeXorParity(byte[] a, byte[] b, byte[] c) {
        int len = Math.max(Math.max(a.length, b.length), c.length);
        byte[] parity = new byte[len];
        for (int i = 0; i < len; i++) {
            byte ba = i < a.length ? a[i] : 0;
            byte bb = i < b.length ? b[i] : 0;
            byte bc = i < c.length ? c[i] : 0;
            parity[i] = (byte) (ba ^ bb ^ bc);
        }
        return parity;
    }

    private byte[] xorRecover(byte[] parity, byte[] known1, byte[] known2) {
        int len = parity.length;
        byte[] recovered = new byte[len];
        for (int i = 0; i < len; i++) {
            byte b1 = i < known1.length ? known1[i] : 0;
            byte b2 = i < known2.length ? known2[i] : 0;
            recovered[i] = (byte) (parity[i] ^ b1 ^ b2);
        }
        return recovered;
    }

    // ─── I/O Utilities ───────────────────────────────────────────────────────

    private void writeFragment(String nodeDir, String name, byte[] data) throws Exception {
        Files.createDirectories(Paths.get(nodeDir));
        Files.write(Paths.get(nodeDir, name), data);
    }

    private byte[] tryReadFragment(String nodeDir, String name) {
        try {
            return Files.readAllBytes(Paths.get(nodeDir, name));
        } catch (IOException e) {
            return null;
        }
    }
}
