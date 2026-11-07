package br.edu.lab2.carros.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "client_profiles")
public class ClientProfile {
    @Id public UUID id = UUID.randomUUID();
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(nullable = false, unique = true) public UserAccount userAccount;
    @Column(nullable = false, unique = true, length = 30) public String rg;
    @Column(nullable = false, unique = true, length = 14) public String cpf;
    @Column(nullable = false, length = 120) public String name;
    @Column(nullable = false, length = 240) public String address;
    @Column(nullable = false, length = 100) public String profession;
    @OneToMany(mappedBy = "clientProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<Employment> employments = new ArrayList<>();

    protected ClientProfile() {}

    public ClientProfile(UserAccount userAccount, String rg, String cpf, String name, String address, String profession) {
        this.userAccount = userAccount;
        this.rg = rg;
        this.cpf = cpf;
        this.name = name;
        this.address = address;
        this.profession = profession;
    }

    public void replaceEmployments(List<Employment> values) {
        employments.clear();
        values.forEach(value -> {
            value.clientProfile = this;
            employments.add(value);
        });
    }
}
