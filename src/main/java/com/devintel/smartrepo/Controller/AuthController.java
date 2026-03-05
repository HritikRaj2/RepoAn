package com.devintel.smartrepo.Controller;

import com.devintel.smartrepo.Entity.User;
import com.devintel.smartrepo.Repository.UserRepository;
import com.devintel.smartrepo.Security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    // ─── REGISTER ────────────────────────────────────────────────────────
    // POST /api/auth/register
    // Body: { "email": "test@example.com", "password": "mypass123" }
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> request) {

        String email    = request.get("email");
        String password = request.get("password");

        // Validation
        if (email == null || email.isEmpty() || password == null || password.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Email and password are required"
            ));
        }

        // Check if user already exists
        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "User with this email already exists"
            ));
        }

        // Create user
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setCreatedAt(LocalDateTime.now());
        user = userRepository.save(user);

        // Generate JWT token
        Authentication auth = new UsernamePasswordAuthenticationToken(
                email, null, new ArrayList<>()
        );
        String token = jwtUtil.generateToken(auth);

        // Response
        Map<String, Object> response = new HashMap<>();
        response.put("message", "User registered successfully");
        response.put("token", token);
        response.put("email", user.getEmail());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─── LOGIN ───────────────────────────────────────────────────────────
    // POST /api/auth/login
    // Body: { "email": "test@example.com", "password": "mypass123" }
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {

        String email    = request.get("email");
        String password = request.get("password");

        // Validation
        if (email == null || email.isEmpty() || password == null || password.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Email and password are required"
            ));
        }

        // Find user
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "Invalid email or password"
            ));
        }

        // Verify password
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "Invalid email or password"
            ));
        }

        // Generate JWT token
        Authentication auth = new UsernamePasswordAuthenticationToken(
                email, null, new ArrayList<>()
        );
        String token = jwtUtil.generateToken(auth);

        // Response
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Login successful");
        response.put("token", token);
        response.put("email", user.getEmail());

        return ResponseEntity.ok(response);
    }
}