package com.winwin.auth;

import com.winwin.auth.Dtos.LoginRequest;
import com.winwin.auth.Dtos.RegisterRequest;
import com.winwin.auth.Dtos.RegisterResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwtService) {
        this.users = users;
        this.encoder = encoder;
        this.jwtService = jwtService;
    }

    public RegisterResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw emailTaken();
        }
        User user = new User(email, encoder.encode(request.password()));
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) { // гонка: два запроса одновременно
            throw emailTaken();
        }
        return new RegisterResponse(user.getId(), user.getEmail());
    }

    public String login(LoginRequest request) {
        User user = users.findByEmail(normalize(request.email()))
                .filter(u -> encoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        return jwtService.generateToken(user);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }

    private static ResponseStatusException emailTaken() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
    }
}