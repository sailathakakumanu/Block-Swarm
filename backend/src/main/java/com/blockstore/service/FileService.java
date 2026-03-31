package com.blockstore.service;

import com.blockstore.model.FileMetadata;
import com.blockstore.model.User;
import com.blockstore.repository.FileMetadataRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.ArrayList;

import com.blockstore.repository.UserRepository;
import com.blockstore.repository.FileAccessRepository;

@Service
public class FileService {

    private final FileMetadataRepository fileMetadataRepository;
    private final FileAccessRepository fileAccessRepository;
    private final UserRepository userRepository;

    public FileService(FileMetadataRepository fileMetadataRepository, 
                       FileAccessRepository fileAccessRepository,
                       UserRepository userRepository) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.fileAccessRepository = fileAccessRepository;
        this.userRepository = userRepository;
    }

    /**
     * Persist file metadata after a successful upload (new or duplicate).
     */
    public FileMetadata saveFileMetadata(String fileName, String fileHash, User owner, String transactionHash, boolean isPublic) {
        FileMetadata metadata = new FileMetadata(fileName, fileHash, owner, transactionHash);
        metadata.setPublic(isPublic);
        return fileMetadataRepository.save(metadata);
    }

    /**
     * Return all files uploaded by the given user.
     */
    public List<FileMetadata> getFilesByUser(User user) {
        List<FileMetadata> owned = fileMetadataRepository.findByOwner(user);
        List<FileMetadata> shared = fileAccessRepository.findByUser(user).stream()
                .map(com.blockstore.model.FileAccess::getFileMetadata)
                .toList();
        
        java.util.Set<FileMetadata> all = new java.util.LinkedHashSet<>(owned);
        all.addAll(shared);
        return new java.util.ArrayList<>(all);
    }

    /**
     * Fetch a single file owned by the user (for download-by-id).
     */
    public Optional<FileMetadata> getFileByIdAndUser(Long fileId, User owner) {
        return fileMetadataRepository.findByIdAndOwner(fileId, owner);
    }

    /**
     * Return ALL files across all users (admin only).
     */
    public List<FileMetadata> getAllFiles() {
        return fileMetadataRepository.findAll();
    }

    /**
     * Fetch any file by ID regardless of owner (admin only).
     */
    public Optional<FileMetadata> getFileById(Long fileId) {
        return fileMetadataRepository.findById(fileId);
    }

    public boolean existsByFileHash(String fileHash) {
        return fileMetadataRepository.existsByFileHash(fileHash);
    }

    public Optional<FileMetadata> findFirstByFileHash(String fileHash) {
        return fileMetadataRepository.findFirstByFileHash(fileHash);
    }
    
    public List<FileMetadata> findAllByFileHash(String fileHash) {
        return fileMetadataRepository.findAllByFileHash(fileHash);
    }

    /**
     * Grant access to a file for a specific user.
     */
    public void grantAccess(FileMetadata file, User user, String encryptedKey) {
        if (fileAccessRepository.existsByFileMetadataAndUser(file, user)) return;
        com.blockstore.model.FileAccess access = new com.blockstore.model.FileAccess(file, user, encryptedKey);
        fileAccessRepository.save(access);
    }

    /**
     * Retrieve the specific encrypted key for a user and file.
     */
    public Optional<String> getEncryptedKeyForUser(FileMetadata file, User user) {
        return fileAccessRepository.findByFileMetadataAndUser(file, user)
                .map(com.blockstore.model.FileAccess::getEncryptedSymmetricKey);
    }

    /**
     * Check if a user has shared access to a specific file.
     */
    public boolean hasAccessToFile(FileMetadata file, User user) {
        if (user == null || file == null) return false;
        return fileAccessRepository.existsByFileMetadataAndUser(file, user);
    }

    /**
     * Check if a user has shared access to ANY file with the given hash.
     */
    public boolean hasAccess(String fileHash, User user) {
        if (user == null || fileHash == null) return false;
        List<FileMetadata> metas = fileMetadataRepository.findAllByFileHash(fileHash);
        for (FileMetadata meta : metas) {
            if (fileAccessRepository.existsByFileMetadataAndUser(meta, user)) {
                return true;
            }
        }
        return false;
    }

    public List<User> getAllUsersExcept(User current) {
        return userRepository.findAll().stream()
                .filter(u -> !u.getId().equals(current.getId()))
                .toList();
    }

    public List<FileMetadata> getPublicFiles() {
        return fileMetadataRepository.findByIsPublicTrueOrderByUploadTimeDesc();
    }
}
