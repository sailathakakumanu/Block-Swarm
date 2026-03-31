# 🚀 Blockchain-Based Storage System

This project is a decentralized, blockchain-based file storage system featuring hybrid encryption (ChaCha20-Poly1305 + ECC) and role-based access control. It consists of a robust Java/Spring Boot backend that integrates with Ethereum (via Ganache) for immutable metadata storage, and a React frontend for an intuitive user experience.

## 🏗️ Project Structure

- **`blockchain-storage/`**: The Spring Boot backend. Handles file uploads, encryption/decryption, database operations, and interacts with the blockchain via Web3j. It also simulates a decentralized network with edge and storage nodes (`edge_node`, `storage_node1`, `storage_node2`, `storage_node3`).
- **`frontend/`**: The React application providing the user interface for authentication, uploading, downloading, and sharing files.

## 🛠️ Prerequisites

Before you begin, ensure you have the following installed:
- **Java 21** or higher
- **Maven**
- **Node.js** (v16+ recommended) and **npm**
- **MySQL Database** (running on default port `3306`)
- **Ganache** (Local Ethereum blockchain running on `http://127.0.0.1:7545`)

---

## ⚙️ Setup & Installation

### 1. Database Configuration
1. Open your MySQL client and create a new database:
   ```sql
   CREATE DATABASE blockchain_storage;
   ```
2. By default, the application expects the following credentials. If your local MySQL setup differs, update `blockchain-storage/src/main/resources/application.properties`:
   ```properties
   spring.datasource.username=root
   spring.datasource.password=your_password
   ```

### 2. Blockchain Configuration (Ganache)
1. Start Ganache (CLI or GUI) on `http://127.0.0.1:7545`.
2. Ensure the smart contracts (from `blockchain-storage/contracts/`) are deployed to your local Ganache network.
3. Update `blockchain-storage/src/main/resources/application.properties` with a generated private key from Ganache and the newly deployed smart contract address:
   ```properties
   ganache.private.key=<YOUR_GANACHE_PRIVATE_KEY>
   contract.address=<DEPLOYED_CONTRACT_ADDRESS>
   ```

### 3. Backend Setup
1. Open a terminal and navigate to the backend directory:
   ```bash
   cd backend
   ```
2. Clean and build the project using Maven:
   ```bash
   mvn clean install
   ```
3. Run the Spring Boot application:
   ```bash
   mvn spring-boot:run
   ```
   *The backend will start and listen on `http://localhost:8080`.*

> **💡 Note:** The system comes with a pre-configured admin account:
> - **Username:** `admin`
> - **Password:** `admin123`

### 4. Frontend Setup
1. Open a new terminal and navigate to the frontend directory:
   ```bash
   cd frontend
   ```
2. Install the necessary Node.js dependencies:
   ```bash
   npm install
   ```
3. Start the React development server:
   ```bash
   npm start
   ```
   *The frontend will automatically open in your browser at `http://localhost:3000`.*

---

## 🌟 Key Features

- **Hybrid Encryption**: Secures files using a combination of ChaCha20-Poly1305 and ECC for robust, fast data protection.
- **Smart Contract Access Control**: Enforces privacy settings (Public/Private) and enables selective file sharing with other users using blockchain logs.
- **Immutable Metadata**: File hashes, ownership, and access policies are immutably stored on the Ethereum blockchain.
- **Swarm Recovery / Chunking**: Simulates a distributed network with XOR parity redundancy across multiple storage nodes to protect against data loss.
