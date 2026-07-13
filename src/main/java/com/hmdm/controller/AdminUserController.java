package com.hmdm.controller;

import com.hmdm.dto.ApiResponse;
import com.hmdm.entity.User;
import com.hmdm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AdminUserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public ResponseEntity<ApiResponse<List<User>>> list() {
        List<User> users = userRepository.findAll();
        // Never return password hashes
        users.forEach(u -> u.setPassword(null));
        return ResponseEntity.ok(ApiResponse.ok(users));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> me(
            @org.springframework.security.core.annotation.AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "login", currentUser.getLogin(),
                "name",  currentUser.getName()  != null ? currentUser.getName()  : "",
                "email", currentUser.getEmail() != null ? currentUser.getEmail() : "",
                "role",  currentUser.getRole()
        )));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<User>> create(@RequestBody Map<String, String> body) {
        if (userRepository.existsByLogin(body.get("login"))) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Login already exists"));
        }
        User user = User.builder()
                .login(body.get("login"))
                .password(passwordEncoder.encode(body.get("password")))
                .name(body.get("name"))
                .email(body.get("email"))
                .role(body.getOrDefault("role", "ADMIN"))
                .customerId(1L)
                .active(true)
                .build();
        user = userRepository.save(user);
        user.setPassword(null);
        return ResponseEntity.ok(ApiResponse.ok(user));
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@PathVariable Long id,
                                                            @RequestBody Map<String, String> body) {
        return userRepository.findById(id).map(user -> {
            user.setPassword(passwordEncoder.encode(body.get("password")));
            userRepository.save(user);
            return ResponseEntity.ok(ApiResponse.<Void>ok());
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        if (!userRepository.existsById(id)) return ResponseEntity.notFound().build();
        userRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
