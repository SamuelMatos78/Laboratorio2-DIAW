package br.edu.lab2.carros.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_accounts")
public class UserAccount {
    @Id public UUID id = UUID.randomUUID();
    @Column(nullable = false, unique = true, length = 80) public String username;
    @Column(nullable = false, length = 100) public String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) public UserRole role;
    @Column(nullable = false) public boolean enabled = true;
    @Column(nullable = false) public Instant createdAt = Instant.now();

    protected UserAccount() {}

    public UserAccount(String username, String passwordHash, UserRole role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }
}
