package com.blockstore.repository;

import com.blockstore.model.FileMetadata;
import com.blockstore.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {
    List<FileMetadata> findByOwner(User owner);
    Optional<FileMetadata> findByIdAndOwner(Long id, User owner);
    boolean existsByFileHash(String fileHash);
    Optional<FileMetadata> findFirstByFileHash(String fileHash);
    List<FileMetadata> findAllByFileHash(String fileHash);
    List<FileMetadata> findByIsPublicTrueOrderByUploadTimeDesc();
}
