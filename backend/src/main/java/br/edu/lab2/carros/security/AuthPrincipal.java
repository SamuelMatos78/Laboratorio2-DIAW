package br.edu.lab2.carros.security;

import br.edu.lab2.carros.domain.UserRole;

import java.util.UUID;

public record AuthPrincipal(UUID userId, String username, UserRole role) {
}
