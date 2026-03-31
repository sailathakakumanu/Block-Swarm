package com.blockstore.repository;

import com.blockstore.model.FileAccess;
import com.blockstore.model.FileMetadata;
import com.blockstore.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface FileAccessRepository extends JpaRepository<FileAccess, Long> {
    Optional<FileAccess> findByFileMetadataAndUser(FileMetadata fileMetadata, User user);
    boolean existsByFileMetadataAndUser(FileMetadata fileMetadata, User user);
    java.util.List<FileAccess> findByUser(User user);
}
