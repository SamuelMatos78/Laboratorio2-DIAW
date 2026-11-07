package br.edu.lab2.carros.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "cars")
public class Car {
    @Id public UUID id = UUID.randomUUID();
    @Column(length = 30) public String registration;
    @Column(nullable = false) public int year;
    @Column(nullable = false, length = 60) public String make;
    @Column(nullable = false, length = 80) public String model;
    @Column(length = 12) public String plate;
    @Column(length = 40) public String category;
    @Column(length = 30) public String transmission;
    @Column(length = 40) public String fuel;
    @Column(precision = 10, scale = 2) public BigDecimal dailyPrice;
    @Column(length = 1000) public String imageUrl;
    @Column(nullable = false) public boolean available;

    protected Car() {}

    public Car(String registration, int year, String make, String model, String plate) {
        this.registration = registration;
        this.year = year;
        this.make = make;
        this.model = model;
        this.plate = plate;
        this.available = false;
    }
}
