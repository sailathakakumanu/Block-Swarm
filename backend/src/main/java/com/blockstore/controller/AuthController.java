package com.blockstore.controller;

import com.blockstore.model.User;
import com.blockstore.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * AuthController — Signup, Login, Logout, and session check endpoints.
 *
 * Uses HttpSession for simple session-based authentication.
 * The logged-in User object is stored in session under key "user".
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAllExceptions(Exception ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        System.err.println(" UNEXPECTED AUTH ERROR: " + ex.getMessage());
        return ResponseEntity.status(500).body(Map.of(
                "status", "ERROR",
                "message", ex.getMessage() != null ? ex.getMessage() : "Unknown 500 Error",
                "trace", sw.toString()
        ));
    }

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    // ─── SIGNUP ─────────────────────────────────────────────────────────────

    @PostMapping("/signup")
    public ResponseEntity<Map<String, Object>> signup(@RequestBody Map<String, String> body) {
        try {
            String username = body.get("username");
            String password = body.get("password");

            User user = userService.signup(username, password);

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "User registered successfully.",
                    "userId", user.getId(),
                    "username", user.getUsername()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    // ─── LOGIN ──────────────────────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body,
                                                     HttpSession session) {
        try {
            String username = body.get("username");
            String password = body.get("password");

            User user = userService.login(username, password);

            // Store user in session
            session.setAttribute("user", user);

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Login successful.",
                    "userId", user.getId(),
                    "username", user.getUsername(),
                    "role", user.getRole()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    // ─── LOGOUT ─────────────────────────────────────────────────────────────

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Logged out successfully."
        ));
    }

    // ─── SESSION CHECK ──────────────────────────────────────────────────────

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "status", "ERROR",
                    "message", "Not logged in."
            ));
        }
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "userId", user.getId(),
                "username", user.getUsername(),
                "role", user.getRole()
        ));
    }
}
