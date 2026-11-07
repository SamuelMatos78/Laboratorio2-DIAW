package br.edu.lab2.carros.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "rental_requests")
public class RentalRequest {
    @Id public UUID id = UUID.randomUUID();
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(nullable = false) public ClientProfile clientProfile;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(nullable = false) public Car car;
    @ManyToOne(fetch = FetchType.LAZY) public AgentProfile evaluatedBy;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 35) public RequestStatus status = RequestStatus.PENDENTE_ANALISE;
    @Column(nullable = false) public LocalDate startDate;
    @Column(nullable = false) public LocalDate endDate;
    @Column(length = 1000) public String clientNotes;
    @Column(length = 1000) public String financialOpinion;
    @Column(nullable = false) public Instant submittedAt = Instant.now();
    @Column(nullable = false) public Instant updatedAt = Instant.now();

    protected RentalRequest() {}

    public RentalRequest(ClientProfile clientProfile, Car car, LocalDate startDate, LocalDate endDate, String clientNotes) {
        this.clientProfile = clientProfile;
        this.car = car;
        this.startDate = startDate;
        this.endDate = endDate;
        this.clientNotes = clientNotes;
    }
}
