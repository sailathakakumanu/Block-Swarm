package com.blockstore.controller;

import com.blockstore.service.BlockchainService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * StatsController — REST API for system statistics.
 *
 * Provides endpoints to inspect storage node contents, edge cache,
 * and recent blockchain transactions for the monitoring dashboard.
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    @Value("${storage.node1}")
    private String node1;

    @Value("${storage.node2}")
    private String node2;

    @Value("${storage.node3}")
    private String node3;

    @Value("${storage.edge}")
    private String edgeDir;

    private final BlockchainService blockchainService;

    public StatsController(BlockchainService blockchainService) {
        this.blockchainService = blockchainService;
    }

    // ─── Storage Nodes ──────────────────────────────────────────────────────

    @GetMapping("/nodes")
    public ResponseEntity<Map<String, Object>> getNodeStats() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("storage_node1", listFiles(node1));
        response.put("storage_node2", listFiles(node2));
        response.put("storage_node3", listFiles(node3));
        return ResponseEntity.ok(response);
    }

    // ─── Edge Cache ─────────────────────────────────────────────────────────

    @GetMapping("/edge")
    public ResponseEntity<Map<String, Object>> getEdgeStats() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("edge_node", listFiles(edgeDir));
        return ResponseEntity.ok(response);
    }

    // ─── Blockchain ─────────────────────────────────────────────────────────

    @GetMapping("/blockchain")
    public ResponseEntity<Map<String, Object>> getBlockchainStats() {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> txList = blockchainService.getRecentTransactions(10);
            response.put("transactions", txList);
            response.put("defaultAddress", blockchainService.getDefaultAddress());
        } catch (Exception e) {
            response.put("error", e.getMessage());
            response.put("transactions", List.of());
        }
        return ResponseEntity.ok(response);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private List<Map<String, Object>> listFiles(String dirPath) {
        Path dir = Paths.get(dirPath);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(p -> {
                        Map<String, Object> info = new LinkedHashMap<>();
                        info.put("name", p.getFileName().toString());
                        try {
                            info.put("size", Files.size(p));
                        } catch (IOException e) {
                            info.put("size", -1);
                        }
                        return info;
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return List.of();
        }
    }
}
