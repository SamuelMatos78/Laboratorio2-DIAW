package br.edu.lab2.carros.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "employments")
public class Employment {
    @Id public UUID id = UUID.randomUUID();
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(nullable = false) public ClientProfile clientProfile;
    @Column(nullable = false, length = 140) public String employer;
    @Column(nullable = false, precision = 12, scale = 2) public BigDecimal income;

    protected Employment() {}

    public Employment(String employer, BigDecimal income) {
        this.employer = employer;
        this.income = income;
    }
}
