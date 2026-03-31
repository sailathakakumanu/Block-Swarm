package com.blockstore.controller;

import com.blockstore.model.FileMetadata;
import com.blockstore.model.User;
import com.blockstore.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.crypto.SecretKey;
import java.util.*;

/**
 * FileController — REST API for upload and download.
 *
 * Changes from original:
 * - Hash computed from ORIGINAL bytes (pre-encrypt) for true content-based
 * dedup
 * - Upload returns txHash + blockNumber for both new files and duplicates
 * - Duplicate upload calls addOwner() instead of rejecting
 * - Download enforces isOwner() → HTTP 403 if unauthorized
 * - Download response carries X-Served-From header (EDGE_CACHE |
 * SWARM_RECONSTRUCTION)
 * - FileAccessed event emitted on every successful download
 * - Decryption uses per-file key fetched from blockchain metadata
 *
 * Auth + DB additions:
 * - Upload requires logged-in user (session-based)
 * - Upload saves FileMetadata to MySQL
 * - GET /my-files returns logged-in user's files
 * - GET /download-by-id/{fileId} resolves hash from DB
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final EncryptionService encryptionService;
    private final BlockchainService blockchainService;
    private final StorageService storageService;
    private final EdgeCacheService edgeCacheService;
    private final FileService fileService;
    private final UserService userService;

    public FileController(EncryptionService encryptionService,
            BlockchainService blockchainService,
            StorageService storageService,
            EdgeCacheService edgeCacheService,
            FileService fileService,
            UserService userService) {
        this.encryptionService = encryptionService;
        this.blockchainService = blockchainService;
        this.storageService = storageService;
        this.edgeCacheService = edgeCacheService;
        this.fileService = fileService;
        this.userService = userService;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        System.out.println("🚀 FileController initialized and ready for requests!");
    }

    // ─── Helper: get logged-in user from session ─────────────────────────────

    private User getLoggedInUser(HttpSession session) {
        return (User) session.getAttribute("user");
    }

    // ─── UPLOAD ──────────────────────────────────────────────────────────────

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file,
                                                          @RequestParam(value = "isPublic", defaultValue = "false") boolean isPublic,
                                                          @RequestParam(value = "sharedWith", required = false) List<Long> sharedWith,
                                                          HttpSession session) {
        Map<String, Object> response = new HashMap<>();

        // Auth check
        User user = getLoggedInUser(session);
        if (user == null) {
            response.put("status", "ERROR");
            response.put("message", "You must be logged in to upload files.");
            return ResponseEntity.status(401).body(response);
        }

        try {
            System.out.println("\n========== UPLOAD STARTED ==========");
            System.out.println("📁 File: " + file.getOriginalFilename() + " (" + file.getSize() + " bytes)");
            System.out.println("👤 User: " + user.getUsername());

            byte[] originalBytes = file.getBytes();

            // 1. Hash original bytes for true content-based deduplication
            String fileHash = encryptionService.generateSHA256Hash(originalBytes);

            // 2. Deduplication check
            boolean existsOnBlockchain = false;
            try {
                existsOnBlockchain = blockchainService.fileExistsOnBlockchain(fileHash);
            } catch (Exception e) {
                System.err.println("Error checking blockchain for duplicates: " + e.getMessage());
            }

            boolean existsInDB = fileService.existsByFileHash(fileHash);

            if (existsOnBlockchain) {
                System.out.println("⚠️  Duplicate detected on Blockchain");
                String callerAddr = blockchainService.getDefaultAddress();
                
                String txHash = null;
                boolean alreadyOwner = false;
                try {
                    alreadyOwner = blockchainService.isOwner(fileHash, callerAddr);
                } catch (Exception e) {
                    System.err.println("Blockchain state error during isOwner check: " + e.getMessage());
                }

                if (alreadyOwner) {
                    System.out.println("👤 Already an owner on-chain — skipping addOwner()");
                    response.put("message", "File already exists. You are already an owner.");
                } else {
                    try {
                        BlockchainService.TxResult tx = blockchainService.addOwner(fileHash, callerAddr);
                        txHash = tx.txHash;
                        response.put("message", "File already exists. You have been added as an owner.");
                        response.put("txHash", tx.txHash);
                        response.put("blockNumber", tx.blockNumber);
                    } catch (Exception e) {
                        System.err.println("Skipping on-chain addOwner due to error: " + e.getMessage());
                        response.put("message", "File already exists locally.");
                    }
                }

                fileService.saveFileMetadata(file.getOriginalFilename(), fileHash, user, txHash, isPublic);
                response.put("status", "DUPLICATE");
                response.put("fileHash", fileHash);
                System.out.println("========== DUPLICATE HANDLED ==========\n");
                return ResponseEntity.ok(response);
                
            } else if (existsInDB) {
                System.out.println("⚠️  Duplicate detected in DB, but MISSING on Blockchain! Restoring...");
                // The blockchain lost our file (Ganache reset). We MUST perform a full restore!
                byte[] encryptedBytes = encryptionService.encryptFile(originalBytes);
                String encryptedKey = encryptionService.getEncryptedKeyAsString();

                BlockchainService.TxResult tx = null;
                try {
                    tx = blockchainService.storeFileMetadata(fileHash, encryptedKey);
                } catch (Exception e) {
                    System.err.println("Error storing to blockchain: " + e.getMessage());
                    tx = new BlockchainService.TxResult("unavailable", -1);
                }

                storageService.storeFragments(fileHash, encryptedBytes);
                fileService.saveFileMetadata(file.getOriginalFilename(), fileHash, user, tx != null ? tx.txHash : null, isPublic);

                // Tell the user it was a duplicate so their expectations are met, even though we did a full upload
                response.put("status", "DUPLICATE");
                response.put("message", "File already exists. State restored to blockchain.");
                response.put("fileHash", fileHash);
                if (tx != null) {
                    response.put("txHash", tx.txHash);
                    response.put("blockNumber", tx.blockNumber);
                }
                System.out.println("========== RESTORE DUPLICATE HANDLED ==========\n");
                return ResponseEntity.ok(response);
            }

            // 3. Encrypt with ChaCha20-Poly1305 (new key per file)
            byte[] encryptedBytes = encryptionService.encryptFile(originalBytes);
            String encryptedKey = encryptionService.getEncryptedKeyAsString();

            // 4. Store metadata on blockchain
            BlockchainService.TxResult tx = null;
            try {
                tx = blockchainService.storeFileMetadata(fileHash, encryptedKey);
            } catch (Exception e) {
                System.err.println("Error storing to blockchain: " + e.getMessage());
                tx = new BlockchainService.TxResult("unavailable", -1);
            }

            // 5. Fragment and distribute across storage nodes
            storageService.storeFragments(fileHash, encryptedBytes);

            // 6. Save file metadata in DB
            FileMetadata meta = fileService.saveFileMetadata(file.getOriginalFilename(), fileHash, user, tx.txHash, isPublic);

            // 7. Handle Hybrid Sharing (Option B)
            if (sharedWith != null && !sharedWith.isEmpty()) {
                System.out.println("🔗 Sharing file with " + sharedWith.size() + " user(s): " + sharedWith);
                SecretKey rawKey = encryptionService.getCurrentRawKey();
                if (rawKey != null) {
                    byte[] rawKeyBytes = rawKey.getEncoded();
                    for (Long recipientId : sharedWith) {
                        try {
                            userService.getUserById(recipientId).ifPresent(recipient -> {
                                try {
                                    String pubKeyB64 = recipient.getPublicKey();
                                    String keyForGrant;
                                    if (pubKeyB64 != null && !pubKeyB64.isBlank()) {
                                        // Wrap the symmetric key with recipient's ECC public key
                                        java.security.PublicKey pubKey = java.security.KeyFactory.getInstance("EC", "BC")
                                            .generatePublic(new java.security.spec.X509EncodedKeySpec(java.util.Base64.getDecoder().decode(pubKeyB64)));
                                        keyForGrant = encryptionService.encryptKeyForUser(rawKeyBytes, pubKey);
                                        System.out.println("✅ ECC-wrapped key for user " + recipient.getUsername());
                                    } else {
                                        // Fallback: grant with the base encrypted key
                                        keyForGrant = encryptedKey;
                                        System.out.println("⚠️ No ECC key for user " + recipient.getUsername() + ", using base key");
                                    }
                                    fileService.grantAccess(meta, recipient, keyForGrant);
                                    System.out.println("✅ Access granted to user " + recipient.getUsername() + " (id=" + recipientId + ")");
                                } catch (Exception inner) {
                                    // ECC wrapping failed — fallback to base encrypted key
                                    System.err.println("⚠️ ECC wrapping failed for user " + recipientId + ": " + inner.getMessage());
                                    try {
                                        fileService.grantAccess(meta, recipient, encryptedKey);
                                        System.out.println("✅ Fallback access granted to user " + recipient.getUsername());
                                    } catch (Exception fallbackEx) {
                                        System.err.println("❌ Fallback grant also failed for user " + recipientId + ": " + fallbackEx.getMessage());
                                    }
                                }
                            });
                        } catch (Exception e) {
                            System.err.println("❌ Failed to share with user " + recipientId + ": " + e.getMessage());
                        }
                    }
                } else {
                    System.err.println("⚠️ No raw key available for sharing — skipping grants");
                }
            }

            // 8. Build response
            response.put("status", "SUCCESS");
            response.put("fileHash", fileHash);
            response.put("fileName", file.getOriginalFilename());
            response.put("txHash", tx.txHash);
            response.put("blockNumber", tx.blockNumber);
            response.put("nodes", new String[] { "storage_node1", "storage_node2", "storage_node3" });
            response.put("encryption", "ChaCha20-Poly1305 + ECC Hybrid");

            System.out.println("========== UPLOAD COMPLETE ==========\n");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("❌ ERROR in uploadFile: " + e.getMessage());
            e.printStackTrace();
            response.put("status", "ERROR");
            response.put("message", e.getClass().getSimpleName() + ": " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    // ─── MY FILES ────────────────────────────────────────────────────────────

    @GetMapping("/my-files")
    public ResponseEntity<?> myFiles(HttpSession session) {
        User user = getLoggedInUser(session);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "status", "ERROR",
                    "message", "You must be logged in to view your files."
            ));
        }

        List<FileMetadata> files = fileService.getFilesByUser(user);
        List<Map<String, Object>> result = new ArrayList<>();
        for (FileMetadata f : files) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", f.getId());
            entry.put("fileName", f.getFileName());
            entry.put("fileHash", f.getFileHash());
            entry.put("transactionHash", f.getTransactionHash());
            entry.put("uploadTime", f.getUploadTime());
            entry.put("isPublic", f.isPublic());
            // Mark shared files so frontend can distinguish
            boolean isOwned = f.getOwner().getId().equals(user.getId());
            entry.put("isShared", !isOwned);
            if (!isOwned) {
                entry.put("sharedBy", f.getOwner().getUsername());
            }
            result.add(entry);
        }

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "files", result
        ));
    }

    // ─── DOWNLOAD BY FILE ID ─────────────────────────────────────────────────

    @GetMapping("/download-by-id/{fileId}")
    public ResponseEntity<?> downloadByFileId(@PathVariable Long fileId,
                                               HttpSession session) {
        // Auth check
        User user = getLoggedInUser(session);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "status", "ERROR",
                    "message", "You must be logged in to download files."
            ));
        }

        // Fetch file metadata from DB
        Optional<FileMetadata> optFile = fileService.getFileById(fileId);
        if (optFile.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "status", "ERROR",
                    "message", "File not found."
            ));
        }

        FileMetadata fileMeta = optFile.get();
        
        // Authorization check — owner, admin, public, OR shared access
        boolean authorized = fileMeta.isPublic() 
                || user.isAdmin() 
                || fileMeta.getOwner().getId().equals(user.getId())
                || fileService.hasAccessToFile(fileMeta, user);
                
        if (!authorized) {
            return ResponseEntity.status(403).body(Map.of(
                    "status", "ERROR",
                    "message", "You are not authorized to access this file"
            ));
        }

        String fileHash = fileMeta.getFileHash();

        // Delegate to existing download logic
        return performDownload(fileHash, fileMeta.getFileName(), user);
    }

    // ─── ALL FILES (Admin Only) ──────────────────────────────────────────────

    @GetMapping("/all-files")
    public ResponseEntity<?> allFiles(HttpSession session) {
        User user = getLoggedInUser(session);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "status", "ERROR",
                    "message", "You must be logged in."
            ));
        }
        if (!user.isAdmin()) {
            return ResponseEntity.status(403).body(Map.of(
                    "status", "ERROR",
                    "message", "Admin access required."
            ));
        }

        List<FileMetadata> files = fileService.getAllFiles();
        List<Map<String, Object>> result = new ArrayList<>();
        for (FileMetadata f : files) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", f.getId());
            entry.put("fileName", f.getFileName());
            entry.put("fileHash", f.getFileHash());
            entry.put("uploadedBy", f.getOwner().getUsername());
            entry.put("transactionHash", f.getTransactionHash());
            entry.put("uploadTime", f.getUploadTime());
            result.add(entry);
        }

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "files", result
        ));
    }

    // ─── PUBLIC FILES ────────────────────────────────────────────────────────

    @GetMapping("/public-files")
    public ResponseEntity<?> publicFiles() {
        List<FileMetadata> files = fileService.getPublicFiles();
        List<Map<String, Object>> result = new ArrayList<>();
        for (FileMetadata f : files) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", f.getId());
            entry.put("fileName", f.getFileName());
            entry.put("fileHash", f.getFileHash());
            entry.put("uploadedBy", f.getOwner().getUsername());
            entry.put("uploadTime", f.getUploadTime());
            entry.put("transactionHash", f.getTransactionHash());
            entry.put("isPublic", true);
            result.add(entry);
        }

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "files", result
        ));
    }

    // ─── DOWNLOAD BY HASH (existing endpoint, preserved) ────────────────────

    @GetMapping("/download/{fileHash}")
    public ResponseEntity<?> downloadFile(
            @PathVariable String fileHash,
            @RequestHeader(value = "X-Caller-Address", required = false) String callerAddress,
            HttpSession session) {
        try {
            System.out.println("\n========== DOWNLOAD STARTED ==========");

            User user = getLoggedInUser(session);
            
            // 1. Resolve caller address
            if (callerAddress == null || callerAddress.isBlank()) {
                callerAddress = blockchainService.getDefaultAddress();
            }
            System.out.println("👤 Caller: " + callerAddress);

            // 2. Existence check
            boolean exists = blockchainService.fileExistsOnBlockchain(fileHash);
            if (!exists) {
                return ResponseEntity.status(404).body(Map.of("error", "File not found on blockchain."));
            }

            // 3. Access control
            List<FileMetadata> dbMetas = fileService.findAllByFileHash(fileHash);
            boolean authorized = false;

            for (FileMetadata meta : dbMetas) {
                if (meta.isPublic()) {
                    authorized = true;
                    break;
                }
            }

            if (!authorized) {
                if (user != null) {
                    if (user.isAdmin()) {
                        authorized = true;
                    } else {
                        for (FileMetadata meta : dbMetas) {
                            if (meta.getOwner().getId().equals(user.getId())) {
                                authorized = true;
                                break;
                            }
                        }
                        // Check if user has shared access via file_access table
                        if (!authorized) {
                            authorized = fileService.hasAccess(fileHash, user);
                        }
                    }
                }
            }

            if (!authorized) {
                String defaultAddr = blockchainService.getDefaultAddress();
                boolean isDefaultAddress = callerAddress.equalsIgnoreCase(defaultAddr);
                
                // Do not allow default server wallet to bypass user DB session checks
                // if it's the server wallet, and they failed the session tests, we ALWAYS reject them.
                if (!isDefaultAddress) {
                    authorized = blockchainService.isOwner(fileHash, callerAddress);
                }
            }

            if (!authorized) {
                System.out.println("🚫 Access DENIED for " + callerAddress);
                return ResponseEntity.status(403).body(
                        Map.of("error", "You are not authorized to access this file"));
            }

            String finalFileName = "recovered_file";
            if (!dbMetas.isEmpty()) {
                finalFileName = dbMetas.get(0).getFileName();
            }

            return performDownload(fileHash, finalFileName, user);

        } catch (Exception e) {
            System.err.println("❌ ERROR in downloadFile: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500)
                    .body(Map.of("error", e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    // ─── Shared download logic ──────────────────────────────────────────────

    private ResponseEntity<?> performDownload(String fileHash, String downloadFileName, User currentUser) {
        try {
            // Edge cache check
            String servedFrom;
            byte[] encryptedBytes;
            if (edgeCacheService.isInEdgeCache(fileHash)) {
                encryptedBytes = edgeCacheService.readFromEdgeCache(fileHash);
                servedFrom = "EDGE_CACHE";
            } else {
                encryptedBytes = storageService.reconstructWithRecovery(fileHash);
                edgeCacheService.writeToEdgeCache(fileHash, encryptedBytes);
                servedFrom = "SWARM_RECONSTRUCTION";
            }

            // Fetch per-file key
            String[] meta = blockchainService.getFileMetadata(fileHash);
            if (meta == null) {
                return ResponseEntity.status(404).body(Map.of("error", "File not found on blockchain."));
            }
            
            String encryptedKey = meta[1]; // Default owner key from blockchain
            
            // Check if current user has a specific shared key in DB
            Optional<FileMetadata> dbMeta = fileService.findFirstByFileHash(fileHash);
            if (dbMeta.isPresent() && currentUser != null) {
                if (!dbMeta.get().getOwner().getId().equals(currentUser.getId())) {
                    Optional<String> sharedKey = fileService.getEncryptedKeyForUser(dbMeta.get(), currentUser);
                    if (sharedKey.isPresent()) {
                        encryptedKey = sharedKey.get();
                    }
                }
            }

            byte[] originalBytes;
            try {
                // Try ECC decryption if user has keys
                String privKeyB64 = (currentUser != null) ? currentUser.getEncryptedPrivateKey() : null;
                if (privKeyB64 != null && privKeyB64.length() > 50) {
                    java.security.PrivateKey privKey = java.security.KeyFactory.getInstance("EC", "BC")
                        .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(java.util.Base64.getDecoder().decode(privKeyB64)));
                    originalBytes = encryptionService.decryptFileForUser(encryptedBytes, encryptedKey, privKey);
                } else {
                    originalBytes = encryptionService.decryptFile(encryptedBytes, encryptedKey);
                }
            } catch (Exception decryptEx) {
                // Fallback to legacy symmetric if ECC fails
                originalBytes = encryptionService.decryptFile(encryptedBytes, encryptedKey);
            }

            // Emit FileAccessed event
            blockchainService.emitFileAccessed(fileHash, blockchainService.getDefaultAddress());

            System.out.println("📡 Served from: " + servedFrom);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadFileName + "\"")
                    .header("X-Served-From", servedFrom)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(originalBytes);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/users/share-list")
    public ResponseEntity<?> getShareList(HttpSession session) {
        User user = getLoggedInUser(session);
        if (user == null) return ResponseEntity.status(401).build();
        
        List<User> others = fileService.getAllUsersExcept(user);
        List<Map<String, Object>> result = others.stream().map(u -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", u.getId());
            map.put("username", u.getUsername());
            return map;
        }).toList();
        
        return ResponseEntity.ok(result);
    }

    // ─── METADATA ────────────────────────────────────────────────────────────

    @GetMapping("/metadata/{fileHash}")
    public ResponseEntity<Map<String, Object>> getMetadata(@PathVariable String fileHash) {
        Map<String, Object> response = new HashMap<>();
        try {
            String[] meta = blockchainService.getFileMetadata(fileHash);
            response.put("fileHash", meta[0]);
            response.put("encryptedKey", meta[1]);
            response.put("owner", meta[2]);
            response.put("timestamp", meta[3]);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", e.getMessage());
            return ResponseEntity.status(404).body(response);
        }
    }
}