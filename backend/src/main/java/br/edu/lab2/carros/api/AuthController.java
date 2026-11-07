package br.edu.lab2.carros.api;

import br.edu.lab2.carros.api.ApiDtos.AuthResponse;
import br.edu.lab2.carros.api.ApiDtos.CurrentUserResponse;
import br.edu.lab2.carros.api.ApiDtos.LoginRequest;
import br.edu.lab2.carros.api.ApiDtos.RegisterAgentRequest;
import br.edu.lab2.carros.api.ApiDtos.RegisterClientRequest;
import br.edu.lab2.carros.security.AuthPrincipal;
import br.edu.lab2.carros.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> registerClient(@Valid @RequestBody RegisterClientRequest request) {
        AuthResponse identity = auth.registerClient(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(auth.createSession(identity));
    }

    @PostMapping("/register-agent")
    public ResponseEntity<AuthResponse> registerAgent(@Valid @RequestBody RegisterAgentRequest request) {
        AuthResponse identity = auth.registerAgent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(auth.createSession(identity));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.createSession(auth.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
        auth.logout(token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return auth.current(principal);
    }
}
