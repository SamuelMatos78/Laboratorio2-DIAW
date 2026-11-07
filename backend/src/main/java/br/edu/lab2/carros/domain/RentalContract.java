package br.edu.lab2.carros.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rental_contracts")
public class RentalContract {
    @Id public UUID id = UUID.randomUUID();
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(nullable = false, unique = true) public RentalRequest rentalRequest;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(nullable = false) public Car car;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) public OwnerType ownerType;
    @Column(nullable = false, length = 140) public String ownerName;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(nullable = false) public AgentProfile executedBy;
    @Column(nullable = false) public Instant executedAt = Instant.now();

    protected RentalContract() {}

    public RentalContract(RentalRequest rentalRequest, Car car, OwnerType ownerType, String ownerName, AgentProfile executedBy) {
        this.rentalRequest = rentalRequest;
        this.car = car;
        this.ownerType = ownerType;
        this.ownerName = ownerName;
        this.executedBy = executedBy;
    }
}
