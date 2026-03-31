package com.blockstore.model;

import jakarta.persistence.*;

@Entity
@Table(name = "file_access")
public class FileAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private FileMetadata fileMetadata;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 2000, nullable = false)
    private String encryptedSymmetricKey;

    public FileAccess() {}

    public FileAccess(FileMetadata fileMetadata, User user, String encryptedSymmetricKey) {
        this.fileMetadata = fileMetadata;
        this.user = user;
        this.encryptedSymmetricKey = encryptedSymmetricKey;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public FileMetadata getFileMetadata() { return fileMetadata; }
    public void setFileMetadata(FileMetadata fileMetadata) { this.fileMetadata = fileMetadata; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getEncryptedSymmetricKey() { return encryptedSymmetricKey; }
    public void setEncryptedSymmetricKey(String encryptedSymmetricKey) { this.encryptedSymmetricKey = encryptedSymmetricKey; }
}
