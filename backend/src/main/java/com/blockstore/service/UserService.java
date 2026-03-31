package com.blockstore.service;

import com.blockstore.model.User;
import com.blockstore.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final ECCService eccService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserRepository userRepository, ECCService eccService) {
        this.userRepository = userRepository;
        this.eccService = eccService;
    }

    /**
     * Register a new user. Throws if username is already taken.
     */
    public User signup(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be empty.");
        }
        if (password == null || password.length() < 4) {
            throw new IllegalArgumentException("Password must be at least 4 characters.");
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username '" + username + "' is already taken.");
        }

        String hashed = passwordEncoder.encode(password);
        User user = new User(username, hashed);

        try {
            // Generate ECC KeyPair for Option B (Backend Crypto)
            java.security.KeyPair kp = eccService.generateKeyPair();
            String publicKeyBase64 = java.util.Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
            
            // In a real system, we'd encrypt the private key with the user's password.
            // For now, we'll store it Base64 encoded (Option B structural step).
            String privateKeyBase64 = java.util.Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
            
            user.setPublicKey(publicKeyBase64);
            user.setEncryptedPrivateKey(privateKeyBase64);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate user ECC keys: " + e.getMessage());
        }

        return userRepository.save(user);
    }

    /**
     * Authenticate user by username + raw password.
     * Returns the User on success, throws on failure.
     */
    public User login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password."));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Invalid username or password.");
        }
        return user;
    }

    public java.util.Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }
}
