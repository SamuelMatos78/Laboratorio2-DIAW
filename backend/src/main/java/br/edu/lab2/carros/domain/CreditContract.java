package br.edu.lab2.carros.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "credit_contracts")
public class CreditContract {
    @Id public UUID id = UUID.randomUUID();
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(nullable = false, unique = true) public RentalContract rentalContract;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(nullable = false) public AgentProfile bankAgent;
    @Column(nullable = false, precision = 14, scale = 2) public BigDecimal amount;

    protected CreditContract() {}

    public CreditContract(RentalContract rentalContract, AgentProfile bankAgent, BigDecimal amount) {
        this.rentalContract = rentalContract;
        this.bankAgent = bankAgent;
        this.amount = amount;
    }
}
