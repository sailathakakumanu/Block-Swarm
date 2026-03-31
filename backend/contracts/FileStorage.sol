// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

/**
 * FileStorage.sol
 * ─────────────────────────────────────────────────────────────────────────────
 * Scalable Blockchain-Based Storage — Smart Contract
 *
 * Features:
 *  - Content-addressed file registry (SHA-256 hash as key)
 *  - Per-file encrypted key storage (ChaCha20-Poly1305 key, Base64-encoded)
 *  - Multi-owner access control via isOwner() / addOwner()
 *  - FileAccessed event for audit trail
 *
 * Deployment: Remix IDE → Web3 Provider (Ganache port 7545)
 * ─────────────────────────────────────────────────────────────────────────────
 */
contract FileStorage {

    struct FileRecord {
        string  fileHash;
        string  encryptedKey;
        address primaryOwner;
        uint256 timestamp;
        bool    exists;
    }

    mapping(string => FileRecord)              private files;
    mapping(string => address[])               private fileOwners;
    mapping(string => mapping(address => bool)) private ownerIndex;

    event FileStored(string fileHash, address indexed owner, uint256 timestamp);
    event OwnerAdded(string fileHash, address indexed newOwner, uint256 timestamp);
    event FileAccessed(string fileHash, address indexed user, uint256 timestamp);

    // ─── Store File ───────────────────────────────────────────────────────────

    function storeFile(string memory fileHash, string memory encryptedKey) public {
        require(!files[fileHash].exists, "File already exists. Use addOwner.");

        files[fileHash] = FileRecord({
            fileHash:     fileHash,
            encryptedKey: encryptedKey,
            primaryOwner: msg.sender,
            timestamp:    block.timestamp,
            exists:       true
        });

        fileOwners[fileHash].push(msg.sender);
        ownerIndex[fileHash][msg.sender] = true;

        emit FileStored(fileHash, msg.sender, block.timestamp);
    }

    // ─── Add Owner ────────────────────────────────────────────────────────────

    function addOwner(string memory fileHash, address newOwner) public {
        require(files[fileHash].exists, "File does not exist.");
        require(!ownerIndex[fileHash][newOwner], "Already an owner.");

        fileOwners[fileHash].push(newOwner);
        ownerIndex[fileHash][newOwner] = true;

        emit OwnerAdded(fileHash, newOwner, block.timestamp);
    }

    // ─── Access Control ───────────────────────────────────────────────────────

    function isOwner(string memory fileHash, address user) public view returns (bool) {
        return ownerIndex[fileHash][user];
    }

    function fileExists(string memory fileHash) public view returns (bool) {
        return files[fileHash].exists;
    }

    // ─── Emit Access Event ────────────────────────────────────────────────────

    function emitAccess(string memory fileHash, address user) public {
        require(files[fileHash].exists, "File does not exist.");
        emit FileAccessed(fileHash, user, block.timestamp);
    }

    // ─── Read Metadata ────────────────────────────────────────────────────────

    function getFile(string memory fileHash)
        public view
        returns (string memory, string memory, address, uint256)
    {
        require(files[fileHash].exists, "File not found.");
        FileRecord storage r = files[fileHash];
        return (r.fileHash, r.encryptedKey, r.primaryOwner, r.timestamp);
    }

    function getOwners(string memory fileHash) public view returns (address[] memory) {
        return fileOwners[fileHash];
    }
}
