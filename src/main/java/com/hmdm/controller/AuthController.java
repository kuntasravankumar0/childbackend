package com.hmdm.controller;

import com.hmdm.dto.ApiResponse;
import com.hmdm.dto.AuthRequest;
import com.hmdm.dto.AuthResponse;
import com.hmdm.entity.User;
import com.hmdm.security.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getLogin(), request.getPassword())
        );
        User user = (User) authentication.getPrincipal();
        String token = tokenProvider.generateToken(user);
        return ResponseEntity.ok(ApiResponse.ok(
                new AuthResponse(token, user.getLogin(), user.getName(), user.getRole())
        ));
    }
}
