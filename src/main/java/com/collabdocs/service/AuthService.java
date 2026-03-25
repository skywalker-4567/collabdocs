package com.collabdocs.service;

import com.collabdocs.dto.request.AuthRequest;
import com.collabdocs.dto.response.AuthResponse;
import com.collabdocs.entity.User;
import com.collabdocs.exception.DuplicateResourceException;
import com.collabdocs.repository.UserRepository;
import com.collabdocs.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    public AuthResponse.TokenResponse register(AuthRequest.Register request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email already registered: " + request.email());
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .build();

        userRepository.save(user);
        String token = jwtUtil.generateToken(user.getEmail());
        return new AuthResponse.TokenResponse(token, user.getEmail(), user.getFullName());
    }

    public AuthResponse.TokenResponse login(AuthRequest.Login request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );
        User user = userRepository.findByEmail(request.email())
                .orElseThrow();
        String token = jwtUtil.generateToken(user.getEmail());
        return new AuthResponse.TokenResponse(token, user.getEmail(), user.getFullName());
    }
}
