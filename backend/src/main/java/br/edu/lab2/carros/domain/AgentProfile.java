package br.edu.lab2.carros.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "agent_profiles")
public class AgentProfile {
    @Id public UUID id = UUID.randomUUID();
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(nullable = false, unique = true) public UserAccount userAccount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) public AgentType type;
    @Column(nullable = false, length = 140) public String entityName;

    protected AgentProfile() {}

    public AgentProfile(UserAccount userAccount, AgentType type, String entityName) {
        this.userAccount = userAccount;
        this.type = type;
        this.entityName = entityName;
    }
}
